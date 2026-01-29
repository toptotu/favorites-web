package com.favorites.audit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GoAuditServiceImpl implements GoAuditService {
    private static final int CLONE_TIMEOUT_SECONDS = 120;
    private static final String TARGET_TYPE_REPO = "REPO";
    private static final String TARGET_TYPE_LOCAL = "LOCAL";

    @Autowired
    private GoAuditPromptBuilder promptBuilder;
    @Autowired
    private GoAuditLlmClient llmClient;

    private final GoTaintAnalyzer analyzer = new GoTaintAnalyzer();

    @Override
    public GoAuditResult scan(GoAuditRequest request) {
        if (request == null || StringUtils.isBlank(request.getTarget())) {
            throw new IllegalArgumentException("target不能为空");
        }
        String target = request.getTarget().trim();
        String sourceType = normalizeSourceType(request.getSourceType(), target);
        Path root = null;
        Path cleanupRoot = null;
        try {
            if (TARGET_TYPE_REPO.equals(sourceType)) {
                if (!looksLikeRepoUrl(target)) {
                    throw new IllegalArgumentException("仓库地址格式不正确");
                }
                CloneResult cloneResult = cloneRepository(target, sanitizeBranch(request.getBranch()));
                root = cloneResult.repoDir;
                cleanupRoot = cloneResult.cleanupDir;
            } else {
                root = resolveLocalPath(target);
            }
            GoAuditResult result = analyzer.analyze(root, target, sourceType);
            enrichIssues(result);
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        } finally {
            if (cleanupRoot != null) {
                deleteRecursively(cleanupRoot);
            }
        }
    }

    private void enrichIssues(GoAuditResult result) {
        if (result == null || result.getIssues() == null) {
            return;
        }
        for (GoAuditIssue issue : result.getIssues()) {
            String prompt = promptBuilder.buildPrompt(issue);
            GoAuditLlmResult llmResult = llmClient.analyze(issue, prompt);
            issue.setModelPrompt(prompt);
            issue.setSummary(llmResult.getSummary());
            issue.setRisk(llmResult.getRisk());
            issue.setRecommendation(llmResult.getRecommendation());
        }
    }

    private String normalizeSourceType(String sourceType, String target) {
        if (StringUtils.isNotBlank(sourceType)) {
            return sourceType.trim().toUpperCase();
        }
        if (target.startsWith("http://") || target.startsWith("https://") || target.endsWith(".git")) {
            return TARGET_TYPE_REPO;
        }
        return TARGET_TYPE_LOCAL;
    }

    private boolean looksLikeRepoUrl(String target) {
        return target.startsWith("http://") || target.startsWith("https://") || target.endsWith(".git");
    }

    private Path resolveLocalPath(String target) throws IOException {
        Path path = Paths.get(target);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("本地路径不存在: " + path);
        }
        return path;
    }

    private CloneResult cloneRepository(String url, String branch) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("go-audit-");
        Path repoDir = tempDir.resolve("repo");
        List<String> command = new ArrayList<String>();
        command.add("git");
        command.add("clone");
        command.add("--depth");
        command.add("1");
        if (StringUtils.isNotBlank(branch)) {
            command.add("--branch");
            command.add(branch);
        }
        command.add(url);
        command.add(repoDir.toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readStream(process.getInputStream());
        boolean finished = process.waitFor(CLONE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("仓库克隆超时，请稍后重试");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("仓库克隆失败: " + output);
        }
        return new CloneResult(repoDir, tempDir);
    }

    private String sanitizeBranch(String branch) {
        if (StringUtils.isBlank(branch)) {
            return null;
        }
        String trimmed = branch.trim();
        if (!trimmed.matches("[A-Za-z0-9._/-]+")) {
            return null;
        }
        return trimmed;
    }

    private String readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private void deleteRecursively(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static class CloneResult {
        private final Path repoDir;
        private final Path cleanupDir;

        private CloneResult(Path repoDir, Path cleanupDir) {
            this.repoDir = repoDir;
            this.cleanupDir = cleanupDir;
        }
    }
}
