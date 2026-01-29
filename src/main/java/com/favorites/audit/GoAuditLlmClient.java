package com.favorites.audit;

import org.springframework.stereotype.Component;

@Component
public class GoAuditLlmClient {
    public GoAuditLlmResult analyze(GoAuditIssue issue, String prompt) {
        String summary = "检测到" + issue.getCategory() + "风险，污点数据从"
                + issue.getSource() + "流向" + issue.getSink() + "。";
        String risk = buildRisk(issue);
        String recommendation = buildRecommendation(issue);
        return new GoAuditLlmResult(summary, risk, recommendation);
    }

    private String buildRisk(GoAuditIssue issue) {
        if ("HIGH".equalsIgnoreCase(issue.getSeverity())) {
            return "攻击者可能利用该入口触发高危行为，导致命令执行、数据泄露或系统被控。";
        }
        if ("MEDIUM".equalsIgnoreCase(issue.getSeverity())) {
            return "攻击者可借助不受控输入访问或调用敏感资源，造成业务异常或数据泄露。";
        }
        return "该问题可能导致异常行为或权限绕过，需要结合上下文确认影响范围。";
    }

    private String buildRecommendation(GoAuditIssue issue) {
        if (issue.getCategory() != null && issue.getCategory().contains("命令执行")) {
            return "对输入做白名单校验，避免拼接命令；优先使用固定参数数组并限制可执行范围。";
        }
        if (issue.getCategory() != null && issue.getCategory().contains("SQL")) {
            return "使用参数化查询或预编译语句，避免字符串拼接SQL；补充输入合法性校验。";
        }
        if (issue.getCategory() != null && issue.getCategory().contains("文件")) {
            return "对路径进行规范化与白名单控制，禁止包含../等路径穿越字符。";
        }
        if (issue.getCategory() != null && issue.getCategory().contains("SSRF")) {
            return "限制请求目标域名/IP范围，增加协议与端口白名单，避免内网访问。";
        }
        return "为输入参数增加校验和净化，并结合业务设置合理的访问控制。";
    }
}
