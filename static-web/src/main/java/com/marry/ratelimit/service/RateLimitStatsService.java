package com.marry.ratelimit.service;

import com.marry.ratelimit.model.RateLimitStats;
import com.marry.ratelimit.model.DetailedRateLimitStats;
import com.marry.ratelimit.model.RateLimitRecord;

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
     * 获取详细统计信息（按IP和用户维度）
     *
     * @param ruleId 规则ID
     * @return 详细统计信息列表
     */
    List<DetailedRateLimitStats> getDetailedStats(String ruleId);

    /**
     * 获取IP维度统计信息
     *
     * @param ruleId 规则ID
     * @param limit 返回数量限制
     * @return IP统计信息列表
     */
    List<DetailedRateLimitStats> getIpStats(String ruleId, int limit);

    /**
     * 获取用户维度统计信息
     *
     * @param ruleId 规则ID
     * @param limit 返回数量限制
     * @return 用户统计信息列表
     */
    List<DetailedRateLimitStats> getUserStats(String ruleId, int limit);

    /**
     * 记录限流详细记录
     *
     * @param record 限流记录
     */
    void recordRateLimitDetail(RateLimitRecord record);

    /**
     * 获取限流记录
     *
     * @param ruleId 规则ID
     * @param limit 返回数量限制
     * @return 限流记录列表
     */
    List<RateLimitRecord> getRateLimitRecords(String ruleId, int limit);

    /**
     * 获取最近的限流记录
     *
     * @param minutes 时间范围（分钟）
     * @param limit 返回数量限制
     * @return 限流记录列表
     */
    List<RateLimitRecord> getRecentRateLimitRecords(int minutes, int limit);

    /**
     * 获取趋势数据
     *
     * @param minutes 时间范围（分钟）
     * @return 趋势数据
     */
    Map<String, Object> getTrendData(int minutes);

    Map<String, Object> getTrendData(String ruleId, int minutes);
}
