package io.github.jicklin.starter.ratelimit.service;

import io.github.jicklin.starter.ratelimit.model.RateLimitRecord;

import javax.servlet.http.HttpServletRequest;

/**
 * 限流统计服务接口
 *
 * 注意：此接口只负责记录统计数据，不提供查询功能
 * 查询功能应该由使用方（如管理后台）自行实现
 */
public interface RateLimitStatsService {

    /**
     * 记录请求
     *
     * @param ruleId  规则ID
     * @param allowed 是否允许
     */
    void recordRequest(String ruleId, boolean allowed);

    /**
     * 记录请求（带请求上下文，支持IP和用户维度统计）
     *
     * @param request HTTP请求
     * @param ruleId  规则ID
     * @param allowed 是否允许
     */
    void recordRequest(HttpServletRequest request, String ruleId, boolean allowed);

    /**
     * 记录限流详细记录
     *
     * @param record 限流记录
     */
    void recordRateLimitDetail(RateLimitRecord record);
}



