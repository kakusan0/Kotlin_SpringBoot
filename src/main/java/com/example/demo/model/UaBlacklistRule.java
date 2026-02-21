package com.example.demo.model;

public class UaBlacklistRule {
    private Long id;
    private String pattern;
    private String matchType = "EXACT";
    private Boolean deleted = false;

    public UaBlacklistRule() {
    }

    public UaBlacklistRule(Long id, String pattern, String matchType, Boolean deleted) {
        this.id = id;
        this.pattern = pattern;
        this.matchType = matchType != null ? matchType : this.matchType;
        this.deleted = deleted != null ? deleted : this.deleted;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
