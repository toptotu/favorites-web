package com.favorites.audit;

public class GoAuditLlmResult {
    private String summary;
    private String risk;
    private String recommendation;

    public GoAuditLlmResult(String summary, String risk, String recommendation) {
        this.summary = summary;
        this.risk = risk;
        this.recommendation = recommendation;
    }

    public String getSummary() {
        return summary;
    }

    public String getRisk() {
        return risk;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
