package com.marry.starter.ratelimit.service;

import com.marry.starter.ratelimit.model.RateLimitRule;

import java.util.List;

/**
 * 限流配置管理服务接口
 */
public interface RateLimitConfigService {

    /**
     * 保存限流规则
     *
     * @param rule 限流规则
     * @return 保存后的规则
     */
    RateLimitRule saveRule(RateLimitRule rule);

    /**
     * 根据ID获取限流规则
     *
     * @param ruleId 规则ID
     * @return 限流规则
     */
    RateLimitRule getRule(String ruleId);

    /**
     * 获取所有限流规则
     *
     * @return 规则列表
     */
    List<RateLimitRule> getAllRules();

    /**
     * 获取启用的限流规则
     *
     * @return 启用的规则列表
     */
    List<RateLimitRule> getEnabledRules();

    /**
     * 删除限流规则
     *
     * @param ruleId 规则ID
     */
    void deleteRule(String ruleId);

    /**
     * 启用/禁用限流规则
     *
     * @param ruleId 规则ID
     * @param enabled 是否启用
     */
    void toggleRule(String ruleId, boolean enabled);

    /**
     * 检查规则是否存在
     *
     * @param ruleId 规则ID
     * @return 是否存在
     */
    boolean exists(String ruleId);

    /**
     * 更新规则优先级
     *
     * @param ruleId 规则ID
     * @param priority 优先级
     */
    void updatePriority(String ruleId, int priority);
}
