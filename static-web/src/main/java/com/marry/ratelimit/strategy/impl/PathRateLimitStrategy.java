package com.marry.ratelimit.strategy.impl;

import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.model.RateLimitType;
import com.marry.ratelimit.strategy.RateLimitStrategy;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 路径限流策略实现
 */
@Component
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
        // 获取请求路径
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        
        // 移除context path
        if (contextPath != null && !contextPath.isEmpty() && requestURI.startsWith(contextPath)) {
            requestURI = requestURI.substring(contextPath.length());
        }
        
        // 确保路径以/开头
        if (!requestURI.startsWith("/")) {
            requestURI = "/" + requestURI;
        }
        
        return requestURI;
    }
}
