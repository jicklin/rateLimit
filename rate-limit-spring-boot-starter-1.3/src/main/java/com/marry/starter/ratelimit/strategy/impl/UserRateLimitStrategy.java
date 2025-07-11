package com.marry.starter.ratelimit.strategy.impl;

import com.marry.starter.ratelimit.model.RateLimitRule;
import com.marry.starter.ratelimit.strategy.RateLimitStrategy;

import javax.servlet.http.HttpServletRequest;

/**
 * 用户ID限流策略实现
 */
public class UserRateLimitStrategy implements RateLimitStrategy {

    private static final String KEY_PREFIX = "rate_limit:user:";
    private static final String USER_ID_PARAM = "netUserId";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public String generateKey(HttpServletRequest request, RateLimitRule rule) {
        String userId = extractIdentifier(request);
        if (userId == null) {
            // 如果没有用户ID，使用IP作为fallback
            String ip = request.getRemoteAddr();
            return KEY_PREFIX + rule.getId() + ":anonymous:" + ip;
        }
        return KEY_PREFIX + rule.getId() + ":" + userId;
    }

    @Override
    public Integer getRequestLimit(RateLimitRule rule) {
        return rule.getUserRequestLimit() != null ? rule.getUserRequestLimit() : rule.getRefillRate();
    }

    @Override
    public boolean supports(RateLimitRule rule) {
        // 只有启用用户限流时才支持
        return rule.isEnableUserLimit();
    }

    @Override
    public String extractIdentifier(HttpServletRequest request) {
        // 优先从请求参数获取用户ID
        String userId = request.getParameter(USER_ID_PARAM);
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }

        // 从请求头获取用户ID
        userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }

        // 从Session获取用户ID（如果有的话）
        if (request.getSession(false) != null) {
            Object sessionUserId = request.getSession(false).getAttribute("userId");
            if (sessionUserId != null) {
                return sessionUserId.toString();
            }
        }

        // 如果都没有，返回null
        return null;
    }
}
