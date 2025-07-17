package io.github.jicklin.starter.ratelimit.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 限流规则配置模型
 */
public class RateLimitRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    private String id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * Ant风格路径模式
     */
    private String pathPattern;

    /**
     * 支持的HTTP方法列表
     */
    private List<HttpMethod> httpMethods;

    /**
     * 令牌桶容量（突发请求数）
     */
    private int bucketCapacity;

    /**
     * 令牌补充速率（每秒补充的令牌数）
     */
    private int refillRate;

    /**
     * 时间窗口（秒）
     */
    private int timeWindow;

    /**
     * 是否启用IP维度限流
     */
    private boolean enableIpLimit;

    /**
     * IP限流配置 - 每个IP的请求限制
     */
    private Integer ipRequestLimit;

    /**
     * IP限流令牌桶容量
     */
    private Integer ipBucketCapacity;

    /**
     * 是否启用用户维度限流
     */
    private boolean enableUserLimit;

    /**
     * 用户限流配置 - 每个用户的请求限制
     */
    private Integer userRequestLimit;

    /**
     * 用户限流令牌桶容量
     */
    private Integer userBucketCapacity;

    /**
     * 优先级（数字越小优先级越高）
     */
    private int priority;

    /**
     * 创建时间
     */
    private long createTime;

    /**
     * 更新时间
     */
    private long updateTime;

    public RateLimitRule() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
        this.enabled = true;
        this.priority = 100;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public List<HttpMethod> getHttpMethods() {
        return httpMethods;
    }

    public void setHttpMethods(List<HttpMethod> httpMethods) {
        this.httpMethods = httpMethods;
    }

    public int getBucketCapacity() {
        return bucketCapacity;
    }

    public void setBucketCapacity(int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
    }

    public int getRefillRate() {
        return refillRate;
    }

    public void setRefillRate(int refillRate) {
        this.refillRate = refillRate;
    }

    public int getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(int timeWindow) {
        this.timeWindow = timeWindow;
    }

    public boolean isEnableIpLimit() {
        return enableIpLimit;
    }

    public void setEnableIpLimit(boolean enableIpLimit) {
        this.enableIpLimit = enableIpLimit;
    }

    public Integer getIpRequestLimit() {
        return ipRequestLimit;
    }

    public void setIpRequestLimit(Integer ipRequestLimit) {
        this.ipRequestLimit = ipRequestLimit;
    }

    public Integer getIpBucketCapacity() {
        return ipBucketCapacity;
    }

    public void setIpBucketCapacity(Integer ipBucketCapacity) {
        this.ipBucketCapacity = ipBucketCapacity;
    }

    public boolean isEnableUserLimit() {
        return enableUserLimit;
    }

    public void setEnableUserLimit(boolean enableUserLimit) {
        this.enableUserLimit = enableUserLimit;
    }

    public Integer getUserRequestLimit() {
        return userRequestLimit;
    }

    public void setUserRequestLimit(Integer userRequestLimit) {
        this.userRequestLimit = userRequestLimit;
    }

    public Integer getUserBucketCapacity() {
        return userBucketCapacity;
    }

    public void setUserBucketCapacity(Integer userBucketCapacity) {
        this.userBucketCapacity = userBucketCapacity;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitRule that = (RateLimitRule) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RateLimitRule{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", pathPattern='" + pathPattern + '\'' +
                ", bucketCapacity=" + bucketCapacity +
                ", refillRate=" + refillRate +
                ", timeWindow=" + timeWindow +
                ", enableIpLimit=" + enableIpLimit +
                ", enableUserLimit=" + enableUserLimit +
                '}';
    }
}
