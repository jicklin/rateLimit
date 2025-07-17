package io.github.jicklin.starter.ratelimit.strategy.impl;

import io.github.jicklin.starter.ratelimit.model.RateLimitRule;
import io.github.jicklin.starter.ratelimit.strategy.RateLimitStrategy;

import javax.servlet.http.HttpServletRequest;

/**
 * 路径限流策略实现
 */
public class PathRateLimitStrategy implements RateLimitStrategy {

    private static final String KEY_PREFIX = "rate_limit:path:";

    @Override
    public String generateKey(HttpServletRequest request, RateLimitRule rule) {
        String path = extractIdentifier(request);
        return KEY_PREFIX + rule.getId() + ":" + path;
    }

    @Override
    public Integer getRequestLimit(RateLimitRule rule) {
        // 路径限流使用基础的refillRate作为限制
        return rule.getRefillRate();
    }

    @Override
    public boolean supports(RateLimitRule rule) {
        // 路径限流是默认策略，总是支持
        return true;
    }

    @Override
    public String extractIdentifier(HttpServletRequest request) {
        // 使用请求路径作为标识符
        String path = request.getRequestURI();

        // 移除上下文路径
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        // 确保路径以/开头
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return path;
    }
}
