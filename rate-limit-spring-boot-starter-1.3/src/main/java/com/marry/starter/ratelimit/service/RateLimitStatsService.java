package com.marry.starter.ratelimit.service;

import com.marry.starter.ratelimit.model.RateLimitRecord;
import com.marry.starter.ratelimit.model.RateLimitStats;

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
     * 获取全局统计信息
     *
     * @return 全局统计信息
     */
    Map<String, Object> getGlobalStats();


    public void recordRateLimitDetail(RateLimitRecord record);
}



