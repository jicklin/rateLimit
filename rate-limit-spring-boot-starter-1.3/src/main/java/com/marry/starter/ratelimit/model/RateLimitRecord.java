package com.marry.starter.ratelimit.model;

import java.io.Serializable;

/**
 * 限流记录模型 - 记录每次限流的详细信息
 */
public class RateLimitRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    private String id;

    /**
     * 规则ID
     */
    private String ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 请求路径
     */
    private String requestPath;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 是否被阻止
     */
    private boolean blocked;

    /**
     * 阻止原因（路径限流、IP限流、用户限流）
     */
    private String blockReason;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * Referer
     */
    private String referer;

    /**
     * 请求时间
     */
    private long requestTime;

    /**
     * 剩余令牌数
     */
    private long remainingTokens;

    public RateLimitRecord() {
        this.requestTime = System.currentTimeMillis();
    }

    public RateLimitRecord(String ruleId, String ruleName) {
        this();
        this.ruleId = ruleId;
        this.ruleName = ruleName;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public void setRemainingTokens(long remainingTokens) {
        this.remainingTokens = remainingTokens;
    }

    @Override
    public String toString() {
        return "RateLimitRecord{" +
                "id='" + id + '\'' +
                ", ruleId='" + ruleId + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", requestPath='" + requestPath + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", userId='" + userId + '\'' +
                ", blocked=" + blocked +
                ", blockReason='" + blockReason + '\'' +
                ", requestTime=" + requestTime +
                ", remainingTokens=" + remainingTokens +
                '}';
    }
}
