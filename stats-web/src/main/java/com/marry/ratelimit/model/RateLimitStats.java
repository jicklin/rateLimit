package com.marry.ratelimit.model;

import java.io.Serializable;

/**
 * 限流统计信息模型
 */
public class RateLimitStats implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 规则ID
     */
    private String ruleId;
    
    /**
     * 规则名称
     */
    private String ruleName;
    
    /**
     * 总请求数
     */
    private long totalRequests;
    
    /**
     * 允许的请求数
     */
    private long allowedRequests;
    
    /**
     * 被阻止的请求数
     */
    private long blockedRequests;
    
    /**
     * 请求频率（每秒）
     */
    private double requestRate;
    
    /**
     * 阻止率
     */
    private double blockRate;
    
    /**
     * 最后请求时间
     */
    private long lastRequestTime;
    
    /**
     * 统计开始时间
     */
    private long startTime;
    
    /**
     * 统计结束时间
     */
    private long endTime;

    public RateLimitStats() {
        this.startTime = System.currentTimeMillis();
        this.endTime = System.currentTimeMillis();
    }

    public RateLimitStats(String ruleId, String ruleName) {
        this();
        this.ruleId = ruleId;
        this.ruleName = ruleName;
    }

    /**
     * 计算请求频率
     */
    public void calculateRequestRate() {
        long duration = (endTime - startTime) / 1000; // 转换为秒
        if (duration > 0) {
            this.requestRate = (double) totalRequests / duration;
        } else {
            this.requestRate = 0.0;
        }
    }

    /**
     * 计算阻止率
     */
    public void calculateBlockRate() {
        if (totalRequests > 0) {
            this.blockRate = (double) blockedRequests / totalRequests * 100;
        } else {
            this.blockRate = 0.0;
        }
    }

    /**
     * 增加请求计数
     */
    public void incrementTotalRequests() {
        this.totalRequests++;
        this.lastRequestTime = System.currentTimeMillis();
        this.endTime = this.lastRequestTime;
    }

    /**
     * 增加允许请求计数
     */
    public void incrementAllowedRequests() {
        this.allowedRequests++;
    }

    /**
     * 增加阻止请求计数
     */
    public void incrementBlockedRequests() {
        this.blockedRequests++;
    }

    // Getters and Setters
    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getAllowedRequests() {
        return allowedRequests;
    }

    public void setAllowedRequests(long allowedRequests) {
        this.allowedRequests = allowedRequests;
    }

    public long getBlockedRequests() {
        return blockedRequests;
    }

    public void setBlockedRequests(long blockedRequests) {
        this.blockedRequests = blockedRequests;
    }

    public double getRequestRate() {
        return requestRate;
    }

    public void setRequestRate(double requestRate) {
        this.requestRate = requestRate;
    }

    public double getBlockRate() {
        return blockRate;
    }

    public void setBlockRate(double blockRate) {
        this.blockRate = blockRate;
    }

    public long getLastRequestTime() {
        return lastRequestTime;
    }

    public void setLastRequestTime(long lastRequestTime) {
        this.lastRequestTime = lastRequestTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "RateLimitStats{" +
                "ruleId='" + ruleId + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", totalRequests=" + totalRequests +
                ", allowedRequests=" + allowedRequests +
                ", blockedRequests=" + blockedRequests +
                ", requestRate=" + requestRate +
                ", blockRate=" + blockRate +
                '}';
    }
}
