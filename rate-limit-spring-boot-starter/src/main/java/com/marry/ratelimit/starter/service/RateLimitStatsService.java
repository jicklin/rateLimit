package com.marry.ratelimit.starter.service;

import com.marry.ratelimit.starter.model.RateLimitStats;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 限流统计服务接口
 */
public interface RateLimitStatsService {

    /**
     * 记录请求
     *
     * @param ruleId 规则ID
     * @param allowed 是否允许
     */
    void recordRequest(String ruleId, boolean allowed);

    /**
     * 记录请求（带请求上下文，支持IP和用户维度统计）
     *
     * @param request HTTP请求
     * @param ruleId 规则ID
     * @param allowed 是否允许
     */
    void recordRequest(HttpServletRequest request, String ruleId, boolean allowed);

    /**
     * 获取规则统计信息
     *
     * @param ruleId 规则ID
     * @return 统计信息
     */
    RateLimitStats getStats(String ruleId);

    /**
     * 获取所有规则的统计信息
     *
     * @return 统计信息列表
     */
    List<RateLimitStats> getAllStats();

    /**
     * 重置规则统计信息
     *
     * @param ruleId 规则ID
     */
    void resetStats(String ruleId);

    /**
     * 重置所有统计信息
     */
    void resetAllStats();

    /**
     * 获取全局统计信息
     *
     * @return 全局统计信息
     */
    Map<String, Object> getGlobalStats();

    /**
     * 获取实时统计信息（最近一段时间）
     *
     * @param ruleId 规则ID
     * @param minutes 时间范围（分钟）
     * @return 实时统计信息
     */
    RateLimitStats getRealtimeStats(String ruleId, int minutes);

    /**
     * 获取趋势数据
     *
     * @param minutes 时间范围（分钟）
     * @return 趋势数据
     */
    Map<String, Object> getTrendData(int minutes);

    /**
     * 获取指定规则的趋势数据
     *
     * @param ruleId 规则ID
     * @param minutes 时间范围（分钟）
     * @return 趋势数据
     */
    Map<String, Object> getTrendData(String ruleId, int minutes);
}
