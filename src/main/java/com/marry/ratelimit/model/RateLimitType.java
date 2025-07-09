package com.marry.ratelimit.model;

/**
 * 限流类型枚举
 */
public enum RateLimitType {
    /**
     * 基于IP地址限流
     */
    IP("ip", "IP地址限流"),
    
    /**
     * 基于用户ID限流
     */
    USER("user", "用户ID限流"),
    
    /**
     * 基于路径限流
     */
    PATH("path", "路径限流");

    private final String code;
    private final String description;

    RateLimitType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static RateLimitType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RateLimitType type : RateLimitType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
