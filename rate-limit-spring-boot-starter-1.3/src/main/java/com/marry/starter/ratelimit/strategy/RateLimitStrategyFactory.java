package com.marry.starter.ratelimit.strategy;

import com.marry.starter.ratelimit.model.RateLimitRule;

import java.util.List;

/**
 * 限流策略工厂
 */
public class RateLimitStrategyFactory {

    private final List<RateLimitStrategy> strategies;

    public RateLimitStrategyFactory(List<RateLimitStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 根据限流规则获取对应的策略（默认返回路径限流策略）
     *
     * @param rule 限流规则
     * @return 限流策略
     */
    public RateLimitStrategy getStrategy(RateLimitRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("限流规则不能为空");
        }

        // 优先返回路径限流策略（默认策略）
        for (RateLimitStrategy strategy : strategies) {
            if (strategy.getClass().getSimpleName().equals("PathRateLimitStrategy")) {
                return strategy;
            }
        }

        // 如果没找到路径策略，返回第一个支持的策略
        for (RateLimitStrategy strategy : strategies) {
            if (strategy.supports(rule)) {
                return strategy;
            }
        }

        throw new IllegalArgumentException("没有找到支持的限流策略");
    }

    /**
     * 检查是否支持指定的限流规则
     *
     * @param rule 限流规则
     * @return 是否支持
     */
    public boolean isSupported(RateLimitRule rule) {
        if (rule == null) {
            return false;
        }

        for (RateLimitStrategy strategy : strategies) {
            if (strategy.supports(rule)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取IP限流策略
     *
     * @param rule 限流规则
     * @return IP限流策略
     */
    public RateLimitStrategy getIpStrategy(RateLimitRule rule) {
        if (rule == null || !rule.isEnableIpLimit()) {
            return null;
        }

        for (RateLimitStrategy strategy : strategies) {
            if (strategy.getClass().getSimpleName().equals("IpRateLimitStrategy")) {
                return strategy;
            }
        }

        return null;
    }

    /**
     * 获取用户限流策略
     *
     * @param rule 限流规则
     * @return 用户限流策略
     */
    public RateLimitStrategy getUserStrategy(RateLimitRule rule) {
        if (rule == null || !rule.isEnableUserLimit()) {
            return null;
        }

        for (RateLimitStrategy strategy : strategies) {
            if (strategy.getClass().getSimpleName().equals("UserRateLimitStrategy")) {
                return strategy;
            }
        }

        return null;
    }
}
