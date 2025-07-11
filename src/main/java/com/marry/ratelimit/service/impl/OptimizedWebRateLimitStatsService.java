package com.marry.ratelimit.service.impl;

import com.marry.ratelimit.model.DetailedRateLimitStats;
import com.marry.ratelimit.model.RateLimitRecord;
import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.model.RateLimitStats;
import com.marry.ratelimit.service.RateLimitConfigService;
import com.marry.ratelimit.service.RateLimitStatsService;
import com.marry.ratelimit.strategy.impl.IpRateLimitStrategy;
import com.marry.ratelimit.strategy.impl.UserRateLimitStrategy;
import com.marry.ratelimit.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 优化的Web限流统计服务
 * 适配starter的优化统计数据结构，提供Web展示所需的查询功能
 */
@Service
@ConditionalOnProperty(prefix = "rate-limit.stats", name = "optimized", havingValue = "true")
public class OptimizedWebRateLimitStatsService implements RateLimitStatsService {

    private static final Logger logger = LoggerFactory.getLogger(OptimizedWebRateLimitStatsService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RateLimitConfigService configService;

    @Autowired
    private IpRateLimitStrategy ipStrategy;

    @Autowired
    private UserRateLimitStrategy userStrategy;

    @Autowired
    private RedisKeyGenerator keyGenerator;

    // Web端不再自己记录统计，委托给starter处理
    // 这些方法保留接口兼容性，但实际不执行记录操作

    @Override
    public void recordRequest(String ruleId, boolean allowed) {
        // Web端不记录统计，由starter的拦截器自动处理
        logger.debug("Web端不记录统计，由starter处理: {}", ruleId);
    }

    @Override
    public void recordRequest(HttpServletRequest request, String ruleId, boolean allowed) {
        // Web端不记录统计，由starter的拦截器自动处理
        logger.debug("Web端不记录统计，由starter处理: {}", ruleId);
    }

    @Override
    public RateLimitStats getStats(String ruleId) {
        try {
            String key = keyGenerator.generateStatsKey(ruleId);
            Map<Object, Object> statsData = redisTemplate.opsForHash().entries(key);

            RateLimitRule rule = configService.getRule(ruleId);
            String ruleName = rule != null ? rule.getName() : "未知规则";

            RateLimitStats stats = new RateLimitStats(ruleId, ruleName);

            if (!statsData.isEmpty()) {
                stats.setTotalRequests(getLongValue(statsData.get("totalRequests")));
                stats.setAllowedRequests(getLongValue(statsData.get("allowedRequests")));
                stats.setBlockedRequests(getLongValue(statsData.get("blockedRequests")));
                stats.setLastRequestTime(getLongValue(statsData.get("lastRequestTime")));

                stats.calculateRequestRate();
                stats.calculateBlockRate();
            }

            return stats;
        } catch (Exception e) {
            logger.error("获取统计信息异常: " + ruleId, e);
            return new RateLimitStats(ruleId, "未知规则");
        }
    }

    @Override
    public List<RateLimitStats> getAllStats() {
        try {
            List<RateLimitStats> statsList = new ArrayList<>();
            List<RateLimitRule> rules = configService.getAllRules();

            for (RateLimitRule rule : rules) {
                RateLimitStats stats = getStats(rule.getId());
                statsList.add(stats);
            }

            return statsList;
        } catch (Exception e) {
            logger.error("获取所有统计信息异常", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void resetStats(String ruleId) {
        try {
            // 删除基础统计
            String key = keyGenerator.generateStatsKey(ruleId);
            redisTemplate.delete(key);

            // 删除详细统计相关键
            resetDetailedStats(ruleId);

            logger.info("重置统计信息: {}", ruleId);
        } catch (Exception e) {
            logger.error("重置统计信息异常: " + ruleId, e);
        }
    }

    @Override
    public void resetAllStats() {
        try {
            // 删除所有统计相关的键
            Set<String> keys = redisTemplate.keys(keyGenerator.getRedisKeyPrefix() + ":stats*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            // 删除详细统计键
            keys = redisTemplate.keys(keyGenerator.getRedisKeyPrefix() + ":detailed_stats*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            // 删除维度列表键
            keys = redisTemplate.keys(keyGenerator.getRedisKeyPrefix() + ":dimension_list*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            logger.info("重置所有统计信息");
        } catch (Exception e) {
            logger.error("重置所有统计信息异常", e);
        }
    }

    @Override
    public Map<String, Object> getGlobalStats() {
        try {
            Map<String, Object> globalStats = new HashMap<>();
            List<RateLimitStats> allStats = getAllStats();

            long totalRequests = 0;
            long totalAllowed = 0;
            long totalBlocked = 0;
            int activeRules = 0;

            for (RateLimitStats stats : allStats) {
                totalRequests += stats.getTotalRequests();
                totalAllowed += stats.getAllowedRequests();
                totalBlocked += stats.getBlockedRequests();

                if (stats.getTotalRequests() > 0) {
                    activeRules++;
                }
            }

            globalStats.put("totalRequests", totalRequests);
            globalStats.put("totalAllowed", totalAllowed);
            globalStats.put("totalBlocked", totalBlocked);
            globalStats.put("totalRules", allStats.size());
            globalStats.put("activeRules", activeRules);
            globalStats.put("blockRate", totalRequests > 0 ? (double) totalBlocked / totalRequests * 100 : 0.0);

            return globalStats;
        } catch (Exception e) {
            logger.error("获取全局统计信息异常", e);
            return new HashMap<>();
        }
    }

    @Override
    public RateLimitStats getRealtimeStats(String ruleId, int minutes) {
        try {
            // 简化实现：直接返回基础统计
            // 在实际项目中，可以基于时间序列数据实现更精确的实时统计
            RateLimitStats stats = getStats(ruleId);

            // 可以在这里添加基于时间窗口的过滤逻辑
            // 例如：只统计最近N分钟的数据

            return stats;
        } catch (Exception e) {
            logger.error("获取实时统计异常: " + ruleId, e);
            return getStats(ruleId); // 降级到基础统计
        }
    }

    @Override
    public Map<String, Object> getTrendData(int minutes) {
        try {
            List<String> labels = new ArrayList<>();
            List<Long> requests = new ArrayList<>();
            List<Long> blocked = new ArrayList<>();

            // 简化实现：基于当前统计数据生成模拟趋势
            long currentTime = System.currentTimeMillis();
            long interval = minutes * 60 * 1000L / 10; // 分成10个时间点

            // 获取所有规则的当前统计
            List<RateLimitRule> rules = configService.getAllRules();
            long totalCurrentRequests = 0;
            long totalCurrentBlocked = 0;

            for (RateLimitRule rule : rules) {
                RateLimitStats stats = getStats(rule.getId());
                totalCurrentRequests += stats.getTotalRequests();
                totalCurrentBlocked += stats.getBlockedRequests();
            }

            // 生成模拟的趋势数据
            for (int i = 9; i >= 0; i--) {
                long time = currentTime - (i * interval);
                labels.add(formatTime(time));

                // 简化：使用当前数据的平均值作为历史数据
                requests.add(totalCurrentRequests / 10);
                blocked.add(totalCurrentBlocked / 10);
            }

            Map<String, Object> trendData = new HashMap<>();
            trendData.put("labels", labels);
            trendData.put("requests", requests);
            trendData.put("blocked", blocked);

            return trendData;
        } catch (Exception e) {
            logger.error("获取趋势数据异常", e);
            return getEmptyTrendData();
        }
    }

    @Override
    public Map<String, Object> getTrendData(String ruleId, int minutes) {
        try {
            // 基于基础统计生成简单的趋势数据
            // 在实际项目中，可以基于时间序列数据实现更精确的趋势分析
            Map<String, Object> trendData = new HashMap<>();

            List<String> labels = new ArrayList<>();
            List<Long> requests = new ArrayList<>();
            List<Long> blocked = new ArrayList<>();

            // 生成时间标签
            long currentTime = System.currentTimeMillis();
            long interval = minutes * 60 * 1000L / 10; // 分成10个时间点

            for (int i = 9; i >= 0; i--) {
                long time = currentTime - (i * interval);
                labels.add(formatTime(time));

                // 简化实现：使用当前统计数据的平均值
                RateLimitStats stats = getStats(ruleId);
                requests.add(stats.getTotalRequests() / 10);
                blocked.add(stats.getBlockedRequests() / 10);
            }

            trendData.put("labels", labels);
            trendData.put("requests", requests);
            trendData.put("blocked", blocked);

            return trendData;
        } catch (Exception e) {
            logger.error("获取规则趋势数据异常: " + ruleId, e);
            return getEmptyTrendData();
        }
    }

    /**
     * 实现接口中的详细统计方法
     */
    @Override
    public List<DetailedRateLimitStats> getDetailedStats(String ruleId) {
        List<DetailedRateLimitStats> result = new ArrayList<>();

        try {
            // 获取IP统计
            List<DetailedRateLimitStats> ipStats = getDimensionStats(ruleId, "ip");
            result.addAll(ipStats);

            // 获取用户统计
            List<DetailedRateLimitStats> userStats = getDimensionStats(ruleId, "user");
            result.addAll(userStats);

        } catch (Exception e) {
            logger.error("获取详细统计列表异常: " + ruleId, e);
        }

        return result;
    }

    /**
     * 获取IP统计
     */
    @Override
    public List<DetailedRateLimitStats> getIpStats(String ruleId, int limit) {
        List<DetailedRateLimitStats> ipStats = getDimensionStats(ruleId, "ip");

        // 限制返回数量
        if (ipStats.size() > limit) {
            return ipStats.subList(0, limit);
        }

        return ipStats;
    }

    /**
     * 获取用户统计
     */
    @Override
    public List<DetailedRateLimitStats> getUserStats(String ruleId, int limit) {
        List<DetailedRateLimitStats> userStats = getDimensionStats(ruleId, "user");

        // 限制返回数量
        if (userStats.size() > limit) {
            return userStats.subList(0, limit);
        }

        return userStats;
    }

    @Override
    public void recordRateLimitDetail(RateLimitRecord record) {

    }


    @Override
    public List<RateLimitRecord> getRateLimitRecords(String ruleId, int limit) {
        return new ArrayList<>();
    }

    @Override
    public List<RateLimitRecord> getRecentRateLimitRecords(int minutes, int limit) {
        return new ArrayList<>();
    }

    /**
     * 记录详细统计
     */
    private void recordDetailedStats(String ruleId, String dimension, String dimensionValue, boolean allowed) {
        try {
            String key = keyGenerator.generateDetailedStatsKey(ruleId, dimension, dimensionValue);
            String listKey = keyGenerator.generateDimensionListKey(ruleId, dimension);

            // 记录详细统计
            redisTemplate.opsForHash().increment(key, "totalRequests", 1);
            if (allowed) {
                redisTemplate.opsForHash().increment(key, "allowedRequests", 1);
            } else {
                redisTemplate.opsForHash().increment(key, "blockedRequests", 1);
            }
            redisTemplate.opsForHash().put(key, "lastRequestTime", System.currentTimeMillis());

            // 添加到维度列表
            redisTemplate.opsForSet().add(listKey, dimensionValue);

            // 设置过期时间
            redisTemplate.expire(key, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
            redisTemplate.expire(listKey, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);

        } catch (Exception e) {
            logger.error("记录详细统计异常: " + ruleId + ":" + dimension + ":" + dimensionValue, e);
        }
    }

    /**
     * 重置详细统计
     */
    private void resetDetailedStats(String ruleId) {
        try {
            // 删除IP相关统计
            resetDimensionStats(ruleId, "ip");

            // 删除用户相关统计
            resetDimensionStats(ruleId, "user");

        } catch (Exception e) {
            logger.error("重置详细统计异常: " + ruleId, e);
        }
    }

    /**
     * 重置指定维度的统计
     */
    private void resetDimensionStats(String ruleId, String dimension) {
        try {
            String listKey = keyGenerator.generateDimensionListKey(ruleId, dimension);
            Set<Object> dimensionValues = redisTemplate.opsForSet().members(listKey);

            if (dimensionValues != null) {
                for (Object dimensionValue : dimensionValues) {
                    String key = keyGenerator.generateDetailedStatsKey(ruleId, dimension, dimensionValue.toString());
                    redisTemplate.delete(key);
                }
            }

            // 删除维度列表
            redisTemplate.delete(listKey);

        } catch (Exception e) {
            logger.error("重置维度统计异常: " + ruleId + ":" + dimension, e);
        }
    }

    /**
     * 获取优化统计数据（采样和热点统计）
     */
    public Map<String, Object> getDetailedStatsMap(String ruleId) {
        Map<String, Object> result = new HashMap<>();

        try {
            result.put("mode", "optimized");
            result.put("description", "基于采样和热点的优化统计模式");

            // 获取采样统计信息
            Map<String, Object> samplingInfo = getSamplingInfo(ruleId);
            result.put("samplingInfo", samplingInfo);

            // 获取热点IP统计（Top 20）
            Map<String, Object> hotspotIps = getHotspotStats(ruleId, "ip", 20);
            result.put("hotspotIps", hotspotIps);

            // 获取热点用户统计（Top 20）
            Map<String, Object> hotspotUsers = getHotspotStats(ruleId, "user", 20);
            result.put("hotspotUsers", hotspotUsers);

            // 获取聚合统计（最近30分钟，按5分钟窗口）
            Map<String, Object> aggregatedStats = getAggregatedStats(ruleId, 30);
            result.put("aggregatedStats", aggregatedStats);

        } catch (Exception e) {
            logger.error("获取优化统计异常: " + ruleId, e);
            result.put("error", "获取优化统计失败");
        }

        return result;
    }

    /**
     * 获取采样统计信息
     */
    private Map<String, Object> getSamplingInfo(String ruleId) {
        Map<String, Object> samplingInfo = new HashMap<>();

        try {
            // 获取基础统计作为总体参考
            RateLimitStats basicStats = getStats(ruleId);

            samplingInfo.put("sampleRate", "1%");
            samplingInfo.put("description", "使用1%采样率进行统计，大幅减少Redis键数量");

            // 尝试获取IP和用户维度的采样数据
            Map<String, Object> ipSamplingData = getDimensionSamplingData(ruleId, "ip");
            Map<String, Object> userSamplingData = getDimensionSamplingData(ruleId, "user");

            boolean hasActualSamplingData = (boolean) ipSamplingData.get("hasData") || (boolean) userSamplingData.get("hasData");

            if (hasActualSamplingData) {
                // 合并IP和用户的采样数据
                long totalSamples = (long) ipSamplingData.get("totalSamples") + (long) userSamplingData.get("totalSamples");
                long blockedSamples = (long) ipSamplingData.get("blockedSamples") + (long) userSamplingData.get("blockedSamples");

                // 基于采样数据估算总体
                long estimatedTotal = totalSamples * 100; // 1%采样，所以乘以100
                long estimatedBlocked = blockedSamples * 100;
                double estimatedBlockRate = totalSamples > 0 ? (double) blockedSamples / totalSamples * 100 : 0.0;

                samplingInfo.put("actualSamples", totalSamples);
                samplingInfo.put("estimatedTotal", estimatedTotal);
                samplingInfo.put("estimatedBlocked", estimatedBlocked);
                samplingInfo.put("estimatedBlockRate", estimatedBlockRate);
                samplingInfo.put("dataSource", "实际采样数据");
                samplingInfo.put("note", "基于IP和用户维度采样数据估算的总体统计");
                samplingInfo.put("ipSamples", ipSamplingData.get("totalSamples"));
                samplingInfo.put("userSamples", userSamplingData.get("totalSamples"));
            } else {
                // 降级到基础统计
                samplingInfo.put("totalRequests", basicStats.getTotalRequests());
                samplingInfo.put("blockedRequests", basicStats.getBlockedRequests());
                samplingInfo.put("blockRate", basicStats.getBlockRate());
                samplingInfo.put("dataSource", "基础统计数据");
                samplingInfo.put("note", "采样数据暂未生成，显示基础统计");
            }

        } catch (Exception e) {
            logger.error("获取采样信息异常: " + ruleId, e);
            samplingInfo.put("error", "获取采样信息失败");
        }

        return samplingInfo;
    }

    /**
     * 获取指定维度的采样数据
     */
    private Map<String, Object> getDimensionSamplingData(String ruleId, String dimension) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 使用web项目自己的采样统计键名规范
            String sampledKey = "rate_limit:sampled_stats:" + ruleId + ":" + dimension + ":100";
            Map<Object, Object> sampledData = redisTemplate.opsForHash().entries(sampledKey);

            if (!sampledData.isEmpty()) {
                long totalSamples = getLongValue(sampledData.get("totalSamples"));
                long allowedSamples = getLongValue(sampledData.get("allowedSamples"));
                long blockedSamples = getLongValue(sampledData.get("blockedSamples"));

                result.put("hasData", true);
                result.put("totalSamples", totalSamples);
                result.put("allowedSamples", allowedSamples);
                result.put("blockedSamples", blockedSamples);
            } else {
                result.put("hasData", false);
                result.put("totalSamples", 0L);
                result.put("allowedSamples", 0L);
                result.put("blockedSamples", 0L);
            }

        } catch (Exception e) {
            logger.error("获取维度采样数据异常: " + ruleId + ":" + dimension, e);
            result.put("hasData", false);
            result.put("totalSamples", 0L);
            result.put("allowedSamples", 0L);
            result.put("blockedSamples", 0L);
        }

        return result;
    }


    /**
     * 获取聚合统计（按时间窗口）
     */
    private Map<String, Object> getAggregatedStats(String ruleId, int minutes) {
        Map<String, Object> aggregatedStats = new HashMap<>();

        try {
            List<Map<String, Object>> timeWindows = new ArrayList<>();

            long currentTime = System.currentTimeMillis();
            long windowSize = 5 * 60 * 1000L; // 5分钟窗口
            int windowCount = minutes / 5;

            for (int i = 0; i < windowCount; i++) {
                long windowStart = currentTime - (i * windowSize);
                long alignedStart = (windowStart / windowSize) * windowSize;

                Map<String, Object> window = new HashMap<>();
                window.put("timeLabel", formatTime(alignedStart));
                window.put("windowStart", alignedStart);

                // 获取IP和用户维度的聚合数据并合并
                Map<String, Object> ipAggData = getDimensionAggregatedData(ruleId, "ip", alignedStart);
                Map<String, Object> userAggData = getDimensionAggregatedData(ruleId, "user", alignedStart);

                boolean hasActualData = (boolean) ipAggData.get("hasData") || (boolean) userAggData.get("hasData");

                if (hasActualData) {
                    // 合并IP和用户的聚合数据
                    long totalRequests = (long) ipAggData.get("totalRequests") + (long) userAggData.get("totalRequests");
                    long allowedRequests = (long) ipAggData.get("allowedRequests") + (long) userAggData.get("allowedRequests");
                    long blockedRequests = (long) ipAggData.get("blockedRequests") + (long) userAggData.get("blockedRequests");

                    window.put("totalRequests", totalRequests);
                    window.put("allowedRequests", allowedRequests);
                    window.put("blockedRequests", blockedRequests);
                    window.put("dataSource", "实际聚合数据");
                } else {
                    // 如果没有聚合数据，使用基础统计的平均值
                    RateLimitStats stats = getStats(ruleId);
                    window.put("totalRequests", stats.getTotalRequests() / windowCount);
                    window.put("allowedRequests", stats.getAllowedRequests() / windowCount);
                    window.put("blockedRequests", stats.getBlockedRequests() / windowCount);
                    window.put("dataSource", "估算数据");
                }

                timeWindows.add(window);
            }

            aggregatedStats.put("timeWindows", timeWindows);
            aggregatedStats.put("windowSize", "5分钟");
            aggregatedStats.put("totalWindows", windowCount);
            aggregatedStats.put("description", "按时间窗口聚合的统计数据");
            aggregatedStats.put("note", "实际聚合数据需要一段时间积累，初期可能显示估算数据");

        } catch (Exception e) {
            logger.error("获取聚合统计异常: " + ruleId, e);
        }

        return aggregatedStats;
    }

    /**
     * 获取指定维度和时间窗口的聚合数据
     */
    private Map<String, Object> getDimensionAggregatedData(String ruleId, String dimension, long alignedStart) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 使用web项目自己的聚合统计键名规范
            String aggKey = "rate_limit:agg_stats:" + ruleId + ":" + dimension + ":" + alignedStart;
            Map<Object, Object> aggData = redisTemplate.opsForHash().entries(aggKey);

            if (!aggData.isEmpty()) {
                result.put("hasData", true);
                result.put("totalRequests", getLongValue(aggData.get("totalRequests")));
                result.put("allowedRequests", getLongValue(aggData.get("allowedRequests")));
                result.put("blockedRequests", getLongValue(aggData.get("blockedRequests")));
            } else {
                result.put("hasData", false);
                result.put("totalRequests", 0L);
                result.put("allowedRequests", 0L);
                result.put("blockedRequests", 0L);
            }

        } catch (Exception e) {
            logger.error("获取维度聚合数据异常: " + ruleId + ":" + dimension + ":" + alignedStart, e);
            result.put("hasData", false);
            result.put("totalRequests", 0L);
            result.put("allowedRequests", 0L);
            result.put("blockedRequests", 0L);
        }

        return result;
    }



    /**
     * 获取指定维度的统计数据
     */
    private List<DetailedRateLimitStats> getDimensionStats(String ruleId, String dimension) {
        List<DetailedRateLimitStats> statsList = new ArrayList<>();

        try {
            String listKey = keyGenerator.generateDimensionListKey(ruleId, dimension);
            Set<Object> dimensionValues = redisTemplate.opsForSet().members(listKey);

            if (dimensionValues != null) {
                for (Object dimensionValue : dimensionValues) {
                    String key = keyGenerator.generateDetailedStatsKey(ruleId, dimension, dimensionValue.toString());
                    Map<Object, Object> statsData = redisTemplate.opsForHash().entries(key);

                    if (!statsData.isEmpty()) {
                        DetailedRateLimitStats stats = new DetailedRateLimitStats();
                        stats.setRuleId(ruleId);
                        stats.setDimension(dimension);
                        stats.setDimensionValue(dimensionValue.toString());
                        stats.setTotalRequests(getLongValue(statsData.get("totalRequests")));
                        stats.setAllowedRequests(getLongValue(statsData.get("allowedRequests")));
                        stats.setBlockedRequests(getLongValue(statsData.get("blockedRequests")));
                        stats.setLastRequestTime(getLongValue(statsData.get("lastRequestTime")));

                        // 计算阻止率
                        if (stats.getTotalRequests() > 0) {
                            stats.setBlockRate((double) stats.getBlockedRequests() / stats.getTotalRequests() * 100);
                        }

                        statsList.add(stats);
                    }
                }
            }

            // 按总请求数排序
            statsList.sort((a, b) -> Long.compare(b.getTotalRequests(), a.getTotalRequests()));

        } catch (Exception e) {
            logger.error("获取维度统计异常: " + ruleId + ":" + dimension, e);
        }

        return statsList;
    }

    /**
     * 获取热点统计（基于Redis ZSet）
     */
    public Map<String, Object> getHotspotStats(String ruleId, String dimension, int topN) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 使用web项目自己的热点统计键名规范
            String hotspotKey = "rate_limit:hotspot_stats:" + ruleId + ":" + dimension;

            // 从ZSet中获取Top N（按分数倒序）
            Set<ZSetOperations.TypedTuple<Object>> hotspotData = redisTemplate.opsForZSet().reverseRangeWithScores(hotspotKey, 0, topN - 1);

            List<Map<String, Object>> hotspots = new ArrayList<>();

            if (hotspotData != null) {
                for (Object item : hotspotData) {
                    if (item instanceof org.springframework.data.redis.core.DefaultTypedTuple) {
                        org.springframework.data.redis.core.DefaultTypedTuple tuple =
                            (org.springframework.data.redis.core.DefaultTypedTuple) item;

                        Map<String, Object> hotspot = new HashMap<>();
                        hotspot.put("dimensionValue", tuple.getValue());
                        hotspot.put("accessCount", tuple.getScore().longValue());
                        hotspot.put("dimension", dimension);

                        hotspots.add(hotspot);
                    }
                }
            }

            result.put("dimension", dimension);
            result.put("topN", topN);
            result.put("hotspots", hotspots);
            result.put("total", hotspots.size());
            result.put("dataSource", "Redis ZSet热点统计");

        } catch (Exception e) {
            logger.error("获取热点统计异常: " + ruleId + ":" + dimension, e);
            result.put("dimension", dimension);
            result.put("topN", topN);
            result.put("hotspots", new ArrayList<>());
            result.put("total", 0);
            result.put("error", "热点统计数据获取失败");
        }

        return result;
    }




    /**
     * 获取空的趋势数据
     */
    private Map<String, Object> getEmptyTrendData() {
        Map<String, Object> trendData = new HashMap<>();
        trendData.put("labels", new ArrayList<>());
        trendData.put("requests", new ArrayList<>());
        trendData.put("blocked", new ArrayList<>());
        return trendData;
    }

    /**
     * 格式化时间
     */
    private String formatTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestamp),
            java.time.ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * 获取Long值
     */
    private long getLongValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
