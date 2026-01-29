package com.favorites.audit;

import java.util.List;

public class GoAuditResult {
    private String target;
    private String targetType;
    private int filesScanned;
    private int issuesFound;
    private int skippedFiles;
    private List<GoAuditIssue> issues;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
    }

    public int getIssuesFound() {
        return issuesFound;
    }

    public void setIssuesFound(int issuesFound) {
        this.issuesFound = issuesFound;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public void setSkippedFiles(int skippedFiles) {
        this.skippedFiles = skippedFiles;
    }

    public List<GoAuditIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<GoAuditIssue> issues) {
        this.issues = issues;
    }
}
