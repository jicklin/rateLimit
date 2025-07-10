package com.marry.ratelimit.starter.service;

import com.marry.ratelimit.starter.model.RateLimitRule;

import javax.servlet.http.HttpServletRequest;

/**
 * 限流服务接口
 */
public interface RateLimitService {
    
    /**
     * 检查请求是否被限流
     * 
     * @param request HTTP请求
     * @return 是否允许通过（true=允许，false=被限流）
     */
    boolean isAllowed(HttpServletRequest request);
    
    /**
     * 检查指定规则是否允许请求
     * 
     * @param request HTTP请求
     * @param rule 限流规则
     * @return 是否允许通过
     */
    boolean isAllowed(HttpServletRequest request, RateLimitRule rule);
    
    /**
     * 获取剩余令牌数
     * 
     * @param request HTTP请求
     * @param rule 限流规则
     * @return 剩余令牌数
     */
    long getRemainingTokens(HttpServletRequest request, RateLimitRule rule);
    
    /**
     * 重置限流状态
     * 
     * @param request HTTP请求
     * @param rule 限流规则
     */
    void reset(HttpServletRequest request, RateLimitRule rule);
    
    /**
     * 重置所有限流状态
     */
    void resetAll();
}
