package com.marry.starter.ratelimit.strategy;

import com.marry.starter.ratelimit.model.RateLimitRule;

import javax.servlet.http.HttpServletRequest;

/**
 * 限流策略接口
 */
public interface RateLimitStrategy {

    /**
     * 生成限流键
     *
     * @param request HTTP请求
     * @param rule 限流规则
     * @return 限流键
     */
    String generateKey(HttpServletRequest request, RateLimitRule rule);

    /**
     * 获取限流配置的请求限制数
     *
     * @param rule 限流规则
     * @return 请求限制数
     */
    Integer getRequestLimit(RateLimitRule rule);

    /**
     * 检查是否支持该限流类型
     *
     * @param rule 限流规则
     * @return 是否支持
     */
    boolean supports(RateLimitRule rule);

    /**
     * 从请求中提取标识符
     *
     * @param request HTTP请求
     * @return 标识符
     */
    String extractIdentifier(HttpServletRequest request);
}
