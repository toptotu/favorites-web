package com.favorites.audit;

import java.util.List;

public class GoAuditIssue {
    private String id;
    private String title;
    private String severity;
    private String category;
    private String filePath;
    private int lineStart;
    private int lineEnd;
    private String source;
    private String sink;
    private List<GoAuditSliceLine> slice;
    private String summary;
    private String risk;
    private String recommendation;
    private String modelPrompt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getLineStart() {
        return lineStart;
    }

    public void setLineStart(int lineStart) {
        this.lineStart = lineStart;
    }

    public int getLineEnd() {
        return lineEnd;
    }

    public void setLineEnd(int lineEnd) {
        this.lineEnd = lineEnd;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSink() {
        return sink;
    }

    public void setSink(String sink) {
        this.sink = sink;
    }

    public List<GoAuditSliceLine> getSlice() {
        return slice;
    }

    public void setSlice(List<GoAuditSliceLine> slice) {
        this.slice = slice;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getModelPrompt() {
        return modelPrompt;
    }

    public void setModelPrompt(String modelPrompt) {
        this.modelPrompt = modelPrompt;
    }
}
