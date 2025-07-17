package com.marry.ratelimit.strategy.impl;

import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.model.RateLimitType;
import com.marry.ratelimit.strategy.RateLimitStrategy;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * IP限流策略实现
 */
@Component
public class IpRateLimitStrategy implements RateLimitStrategy {
    
    private static final String KEY_PREFIX = "rate_limit:ip:";
    
    @Override
    public String generateKey(HttpServletRequest request, RateLimitRule rule) {
        String ip = extractIdentifier(request);
        return KEY_PREFIX + rule.getId() + ":" + ip;
    }
    
    @Override
    public Integer getRequestLimit(RateLimitRule rule) {
        return rule.getIpRequestLimit() != null ? rule.getIpRequestLimit() : rule.getRefillRate();
    }
    
    @Override
    public boolean supports(RateLimitRule rule) {
        // 只有启用IP限流时才支持
        return rule.isEnableIpLimit();
    }
    
    @Override
    public String extractIdentifier(HttpServletRequest request) {
        // 优先从X-Forwarded-For头获取真实IP
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 从X-Real-IP头获取
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        // 从Proxy-Client-IP头获取
        String proxyClientIp = request.getHeader("Proxy-Client-IP");
        if (proxyClientIp != null && !proxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(proxyClientIp)) {
            return proxyClientIp;
        }
        
        // 从WL-Proxy-Client-IP头获取
        String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
        if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(wlProxyClientIp)) {
            return wlProxyClientIp;
        }
        
        // 最后从RemoteAddr获取
        return request.getRemoteAddr();
    }
}
