package com.favorites.audit;

import org.springframework.stereotype.Component;

@Component
public class GoAuditPromptBuilder {
    public String buildPrompt(GoAuditIssue issue) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是资深Go安全审计专家，请基于以下污点分析结果输出问题描述、风险影响、修复建议。").append("\n");
        prompt.append("问题类型: ").append(issue.getCategory()).append("\n");
        prompt.append("严重级别: ").append(issue.getSeverity()).append("\n");
        prompt.append("文件位置: ").append(issue.getFilePath())
                .append(":").append(issue.getLineStart()).append("-").append(issue.getLineEnd()).append("\n");
        prompt.append("污点来源: ").append(issue.getSource()).append("\n");
        prompt.append("风险汇聚: ").append(issue.getSink()).append("\n");
        prompt.append("代码切片:").append("\n");
        if (issue.getSlice() != null) {
            for (GoAuditSliceLine line : issue.getSlice()) {
                prompt.append("L").append(line.getLine())
                        .append("[").append(line.getKind()).append("] ")
                        .append(line.getCode()).append("\n");
            }
        }
        prompt.append("输出格式: ").append("问题描述/风险影响/修复建议");
        return prompt.toString();
    }
}
