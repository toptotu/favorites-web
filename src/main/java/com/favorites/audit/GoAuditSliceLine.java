package com.favorites.audit;

public class GoAuditSliceLine {
    private int line;
    private String code;
    private String kind;

    public GoAuditSliceLine() {
    }

    public GoAuditSliceLine(int line, String code, String kind) {
        this.line = line;
        this.code = code;
        this.kind = kind;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
