package com.favorites.audit;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GoTaintAnalyzer {
    private static final int MAX_FILES = 1500;
    private static final int MAX_ISSUES = 200;
    private static final int MAX_SLICE_LENGTH = 12;
    private static final long MAX_FILE_BYTES = 512 * 1024;
    private static final List<String> SKIP_SEGMENTS = Arrays.asList(
            "/.git/", "/vendor/", "/node_modules/", "/testdata/", "/third_party/"
    );
    private static final Pattern ASSIGN_PATTERN = Pattern.compile(
            "^\\s*(?:var\\s+)?([a-zA-Z_][\\w]*)(?:\\s*,[^=]+)?\\s*(?::=|=)\\s*(.+)$"
    );

    private static final List<SourceRule> SOURCE_RULES = Arrays.asList(
            new SourceRule("HTTP参数", ".FormValue("),
            new SourceRule("HTTP参数", ".PostFormValue("),
            new SourceRule("HTTP参数", ".Query().Get("),
            new SourceRule("HTTP参数", ".Header.Get("),
            new SourceRule("Gin参数", "c.Query("),
            new SourceRule("Gin参数", "c.PostForm("),
            new SourceRule("Gin参数", "c.Param("),
            new SourceRule("Gin参数", "c.GetHeader("),
            new SourceRule("Echo参数", "c.QueryParam("),
            new SourceRule("Echo参数", "c.FormValue("),
            new SourceRule("环境变量", "os.Getenv("),
            new SourceRule("命令行参数", "flag.String("),
            new SourceRule("命令行参数", "flag.StringVar("),
            new SourceRule("命令行参数", "flag.Int("),
            new SourceRule("命令行参数", "flag.IntVar("),
            new SourceRule("命令行参数", "os.Args[")
    );

    private static final List<SinkRule> SINK_RULES = Arrays.asList(
            new SinkRule("命令执行注入", "exec.Command(", "HIGH"),
            new SinkRule("命令执行注入", "exec.CommandContext(", "HIGH"),
            new SinkRule("SQL注入", ".Query(", "HIGH"),
            new SinkRule("SQL注入", ".QueryRow(", "HIGH"),
            new SinkRule("SQL注入", ".Exec(", "HIGH"),
            new SinkRule("SQL注入", ".Raw(", "HIGH"),
            new SinkRule("SSRF", "http.Get(", "MEDIUM"),
            new SinkRule("SSRF", "http.Post(", "MEDIUM"),
            new SinkRule("SSRF", "http.NewRequest(", "MEDIUM"),
            new SinkRule("文件访问风险", "os.Open(", "MEDIUM"),
            new SinkRule("文件访问风险", "os.OpenFile(", "MEDIUM"),
            new SinkRule("文件访问风险", "os.Create(", "MEDIUM"),
            new SinkRule("文件访问风险", "ioutil.ReadFile(", "MEDIUM"),
            new SinkRule("文件访问风险", "ioutil.WriteFile(", "MEDIUM")
    );

    public GoAuditResult analyze(Path root, String target, String targetType) throws IOException {
        GoAuditResult result = new GoAuditResult();
        result.setTarget(target);
        result.setTargetType(targetType);
        List<GoAuditIssue> issues = new ArrayList<GoAuditIssue>();
        List<Path> goFiles = collectGoFiles(root);
        int skippedFiles = 0;
        for (Path file : goFiles) {
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            if (Files.size(file) > MAX_FILE_BYTES) {
                skippedFiles++;
                continue;
            }
            analyzeFile(root, file, issues);
        }
        result.setFilesScanned(goFiles.size());
        result.setIssuesFound(issues.size());
        result.setIssues(issues);
        result.setSkippedFiles(skippedFiles);
        return result;
    }

    private List<Path> collectGoFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".go"))
                    .filter(path -> !path.getFileName().toString().endsWith("_test.go"))
                    .filter(path -> !shouldSkip(path))
                    .limit(MAX_FILES)
                    .forEach(files::add);
        }
        return files;
    }

    private boolean shouldSkip(Path path) {
        String normalized = path.toString().replace("\\", "/");
        for (String segment : SKIP_SEGMENTS) {
            if (normalized.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private void analyzeFile(Path root, Path file, List<GoAuditIssue> issues) throws IOException {
        Map<String, TaintInfo> tainted = new HashMap<String, TaintInfo>();
        Set<String> reported = new HashSet<String>();
        boolean inBlockComment = false;
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                CommentStripResult stripResult = stripComments(rawLine, inBlockComment);
                inBlockComment = stripResult.inBlockComment;
                String line = stripResult.code;
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String normalized = normalizeWhitespace(trimmed);
                processAssignment(normalized, lineNumber, tainted);
                SinkRule sinkRule = matchSink(normalized);
                if (sinkRule != null) {
                    reportSinkHit(root, file, normalized, lineNumber, sinkRule, tainted, reported, issues);
                }
                if (issues.size() >= MAX_ISSUES) {
                    return;
                }
            }
        }
    }

    private void processAssignment(String code, int lineNumber, Map<String, TaintInfo> tainted) {
        Matcher matcher = ASSIGN_PATTERN.matcher(code);
        if (!matcher.find()) {
            return;
        }
        String varName = matcher.group(1);
        String rhs = matcher.group(2);
        SourceRule sourceRule = matchSource(rhs);
        if (sourceRule != null) {
            List<GoAuditSliceLine> slice = new ArrayList<GoAuditSliceLine>();
            slice.add(new GoAuditSliceLine(lineNumber, code, "SOURCE"));
            tainted.put(varName, new TaintInfo(sourceRule, lineNumber, slice));
            return;
        }
        TaintInfo upstream = findTainted(rhs, tainted);
        if (upstream != null) {
            List<GoAuditSliceLine> slice = new ArrayList<GoAuditSliceLine>(upstream.slice);
            slice.add(new GoAuditSliceLine(lineNumber, code, "PROPAGATION"));
            trimSlice(slice);
            tainted.put(varName, new TaintInfo(upstream.sourceRule, upstream.sourceLine, slice));
        }
    }

    private void reportSinkHit(Path root, Path file, String code, int lineNumber, SinkRule sinkRule,
                               Map<String, TaintInfo> tainted, Set<String> reported, List<GoAuditIssue> issues) {
        TaintInfo taint = findTainted(code, tainted);
        if (taint == null) {
            return;
        }
        String key = file.toString() + ":" + lineNumber + ":" + sinkRule.signature;
        if (reported.contains(key)) {
            return;
        }
        reported.add(key);
        List<GoAuditSliceLine> slice = new ArrayList<GoAuditSliceLine>(taint.slice);
        slice.add(new GoAuditSliceLine(lineNumber, code, "SINK"));
        trimSlice(slice);
        GoAuditIssue issue = new GoAuditIssue();
        issue.setId("GO-AUDIT-" + (issues.size() + 1));
        issue.setTitle(sinkRule.category + "风险");
        issue.setSeverity(sinkRule.severity);
        issue.setCategory(sinkRule.category);
        issue.setFilePath(resolveFilePath(root, file));
        issue.setLineStart(slice.get(0).getLine());
        issue.setLineEnd(slice.get(slice.size() - 1).getLine());
        issue.setSource(taint.sourceRule.name + "(L" + taint.sourceLine + ")");
        issue.setSink(sinkRule.signature + "(L" + lineNumber + ")");
        issue.setSlice(slice);
        issues.add(issue);
    }

    private String resolveFilePath(Path root, Path file) {
        if (Files.isRegularFile(root)) {
            return root.getFileName().toString();
        }
        String relative = root.relativize(file).toString().replace("\\", "/");
        if (relative.isEmpty()) {
            return file.getFileName().toString();
        }
        return relative;
    }

    private TaintInfo findTainted(String code, Map<String, TaintInfo> tainted) {
        for (Map.Entry<String, TaintInfo> entry : tainted.entrySet()) {
            if (containsWord(code, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean containsWord(String code, String word) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
        return pattern.matcher(code).find();
    }

    private SourceRule matchSource(String code) {
        for (SourceRule rule : SOURCE_RULES) {
            if (code.contains(rule.signature)) {
                return rule;
            }
        }
        return null;
    }

    private SinkRule matchSink(String code) {
        for (SinkRule rule : SINK_RULES) {
            if (code.contains(rule.signature)) {
                return rule;
            }
        }
        return null;
    }

    private String normalizeWhitespace(String code) {
        return code.trim().replaceAll("\\s+", " ");
    }

    private void trimSlice(List<GoAuditSliceLine> slice) {
        while (slice.size() > MAX_SLICE_LENGTH) {
            if (slice.size() <= 2) {
                break;
            }
            slice.remove(1);
        }
    }

    private CommentStripResult stripComments(String line, boolean inBlockComment) {
        if (line == null) {
            return new CommentStripResult("", inBlockComment);
        }
        StringBuilder output = new StringBuilder();
        boolean inString = false;
        char stringChar = 0;
        boolean escape = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            char next = (i + 1 < line.length()) ? line.charAt(i + 1) : '\0';
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                output.append(current);
                if (stringChar == '`') {
                    if (current == '`') {
                        inString = false;
                    }
                    continue;
                }
                if (escape) {
                    escape = false;
                    continue;
                }
                if (current == '\\') {
                    escape = true;
                    continue;
                }
                if (current == stringChar) {
                    inString = false;
                }
                continue;
            }
            if (current == '"' || current == '\'' || current == '`') {
                inString = true;
                stringChar = current;
                output.append(current);
                continue;
            }
            if (current == '/' && next == '/') {
                break;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            output.append(current);
        }
        return new CommentStripResult(output.toString(), inBlockComment);
    }

    private static class SourceRule {
        private final String name;
        private final String signature;

        private SourceRule(String name, String signature) {
            this.name = name;
            this.signature = signature;
        }
    }

    private static class SinkRule {
        private final String category;
        private final String signature;
        private final String severity;

        private SinkRule(String category, String signature, String severity) {
            this.category = category;
            this.signature = signature;
            this.severity = severity;
        }
    }

    private static class TaintInfo {
        private final SourceRule sourceRule;
        private final int sourceLine;
        private final List<GoAuditSliceLine> slice;

        private TaintInfo(SourceRule sourceRule, int sourceLine, List<GoAuditSliceLine> slice) {
            this.sourceRule = sourceRule;
            this.sourceLine = sourceLine;
            this.slice = slice;
        }
    }

    private static class CommentStripResult {
        private final String code;
        private final boolean inBlockComment;

        private CommentStripResult(String code, boolean inBlockComment) {
            this.code = code;
            this.inBlockComment = inBlockComment;
        }
    }
}
