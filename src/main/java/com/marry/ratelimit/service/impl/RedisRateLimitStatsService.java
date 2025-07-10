package com.marry.ratelimit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.model.RateLimitStats;
import com.marry.ratelimit.model.DetailedRateLimitStats;
import com.marry.ratelimit.model.RateLimitRecord;
import com.marry.ratelimit.service.RateLimitConfigService;
import com.marry.ratelimit.service.RateLimitStatsService;
import com.marry.ratelimit.strategy.impl.IpRateLimitStrategy;
import com.marry.ratelimit.strategy.impl.UserRateLimitStrategy;
import com.marry.ratelimit.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的限流统计服务实现
 */
@Service
public class RedisRateLimitStatsService implements RateLimitStatsService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitStatsService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RateLimitConfigService configService;

    @Autowired
    private IpRateLimitStrategy ipStrategy;

    @Autowired
    private UserRateLimitStrategy userStrategy;

    @Autowired
    private RedisKeyGenerator redisKeyGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void recordRequest(String ruleId, boolean allowed) {
        try {
            String statsKey = redisKeyGenerator.generateStatsKey(ruleId);

            // 增加总请求数
            redisTemplate.opsForHash().increment(statsKey, "totalRequests", 1);

            if (allowed) {
                // 增加允许请求数
                redisTemplate.opsForHash().increment(statsKey, "allowedRequests", 1);
            } else {
                // 增加阻止请求数
                redisTemplate.opsForHash().increment(statsKey, "blockedRequests", 1);
            }

            // 更新最后请求时间
            redisTemplate.opsForHash().put(statsKey, "lastRequestTime", System.currentTimeMillis());

            // 设置过期时间（7天）
            redisTemplate.expire(statsKey, 7, TimeUnit.DAYS);

            // 记录实时统计（用于计算请求频率）
            recordRealtimeStats(ruleId, allowed);

        } catch (Exception e) {
            logger.error("记录请求统计异常: " + ruleId, e);
        }
    }

    @Override
    public void recordRequest(HttpServletRequest request, String ruleId, boolean allowed) {
        // 先记录基础统计
        recordRequest(ruleId, allowed);

        try {
            // 获取规则配置
            RateLimitRule rule = configService.getRule(ruleId);
            if (rule == null) {
                return;
            }

            // 如果启用了IP维度限流，记录IP统计
            if (rule.isEnableIpLimit()) {
                String ip = ipStrategy.extractIdentifier(request);
                if (ip != null) {
                    recordDetailedStats(ruleId, "ip", ip, allowed);
                }
            }

            // 如果启用了用户维度限流，记录用户统计
            if (rule.isEnableUserLimit()) {
                String userId = userStrategy.extractIdentifier(request);
                if (userId != null) {
                    recordDetailedStats(ruleId, "user", userId, allowed);
                }
            }

            // 记录详细的限流记录
            recordRateLimitDetailFromRequest(request, rule, allowed);

        } catch (Exception e) {
            logger.error("记录详细请求统计异常: " + ruleId, e);
        }
    }

    @Override
    public RateLimitStats getStats(String ruleId) {
        try {
            String statsKey = redisKeyGenerator.generateStatsKey(ruleId);
            Map<Object, Object> statsData = redisTemplate.opsForHash().entries(statsKey);

            if (statsData.isEmpty()) {
                // 如果没有统计数据，创建一个空的统计对象
                RateLimitRule rule = configService.getRule(ruleId);
                String ruleName = rule != null ? rule.getName() : "未知规则";
                return new RateLimitStats(ruleId, ruleName);
            }

            RateLimitStats stats = new RateLimitStats();
            stats.setRuleId(ruleId);

            // 获取规则名称
            RateLimitRule rule = configService.getRule(ruleId);
            stats.setRuleName(rule != null ? rule.getName() : "未知规则");

            // 设置统计数据
            stats.setTotalRequests(getLongValue(statsData, "totalRequests"));
            stats.setAllowedRequests(getLongValue(statsData, "allowedRequests"));
            stats.setBlockedRequests(getLongValue(statsData, "blockedRequests"));
            stats.setLastRequestTime(getLongValue(statsData, "lastRequestTime"));

            // 计算请求频率和阻止率
            stats.calculateRequestRate();
            stats.calculateBlockRate();

            return stats;
        } catch (Exception e) {
            logger.error("获取统计信息异常: " + ruleId, e);
            return new RateLimitStats(ruleId, "错误");
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
            String statsKey = redisKeyGenerator.generateStatsKey(ruleId);
            redisTemplate.delete(statsKey);

            // 删除实时统计数据
            redisTemplate.delete(redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:realtime:" + ruleId + ":*"));

            // 删除详细统计数据（IP和用户维度）
            redisTemplate.delete(redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:detailed_stats:" + ruleId + ":*"));

            // 删除维度列表数据
            redisTemplate.delete(redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:dimension_list:" + ruleId + ":*"));

            logger.info("重置统计信息: {}", ruleId);
        } catch (Exception e) {
            logger.error("重置统计信息异常: " + ruleId, e);
        }
    }

    @Override
    public void resetAllStats() {
        try {
            // 删除所有统计数据
            redisTemplate.delete(redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:stats:*"));
            redisTemplate.delete(redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:realtime:*"));
            redisTemplate.delete(redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:detailed_stats:*"));
            redisTemplate.delete(redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:dimension_list:*"));

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
            globalStats.put("globalBlockRate", totalRequests > 0 ? (double) totalBlocked / totalRequests * 100 : 0.0);

            return globalStats;
        } catch (Exception e) {
            logger.error("获取全局统计信息异常", e);
            return new HashMap<>();
        }
    }

    @Override
    public RateLimitStats getRealtimeStats(String ruleId, int minutes) {
        try {
            RateLimitStats stats = new RateLimitStats(ruleId, "实时统计");

            long endTime = System.currentTimeMillis();
            long startTime = endTime - (minutes * 60 * 1000L);

            // 获取时间范围内的统计数据
            String pattern = redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:realtime:" + ruleId + ":*";
            for (String key : redisTemplate.keys(pattern)) {
                try {
                    String[] parts = key.split(":");
                    long timestamp = Long.parseLong(parts[parts.length - 1]);

                    if (timestamp >= startTime && timestamp <= endTime) {
                        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
                        stats.setTotalRequests(stats.getTotalRequests() + getLongValue(data, "requests"));
                        stats.setAllowedRequests(stats.getAllowedRequests() + getLongValue(data, "allowed"));
                        stats.setBlockedRequests(stats.getBlockedRequests() + getLongValue(data, "blocked"));
                    }
                } catch (Exception e) {
                    // 忽略解析错误的键
                }
            }

            stats.setStartTime(startTime);
            stats.setEndTime(endTime);
            stats.calculateRequestRate();
            stats.calculateBlockRate();

            return stats;
        } catch (Exception e) {
            logger.error("获取实时统计信息异常: " + ruleId, e);
            return new RateLimitStats(ruleId, "错误");
        }
    }

    /**
     * 记录详细统计数据（IP和用户维度）
     */
    private void recordDetailedStats(String ruleId, String dimension, String dimensionValue, boolean allowed) {
        try {
            String detailedStatsKey = redisKeyGenerator.generateDetailedStatsKey(ruleId, dimension, dimensionValue);

            // 增加总请求数
            redisTemplate.opsForHash().increment(detailedStatsKey, "totalRequests", 1);

            if (allowed) {
                // 增加允许请求数
                redisTemplate.opsForHash().increment(detailedStatsKey, "allowedRequests", 1);
            } else {
                // 增加阻止请求数
                redisTemplate.opsForHash().increment(detailedStatsKey, "blockedRequests", 1);
            }

            // 更新最后请求时间
            redisTemplate.opsForHash().put(detailedStatsKey, "lastRequestTime", System.currentTimeMillis());

            // 设置过期时间（7天）
            redisTemplate.expire(detailedStatsKey, 7, TimeUnit.DAYS);

            // 将维度值添加到维度列表中（用于后续查询）
            String dimensionListKey = redisKeyGenerator.generateDimensionListKey(ruleId, dimension);
            redisTemplate.opsForZSet().incrementScore(dimensionListKey, dimensionValue, 1);
            redisTemplate.expire(dimensionListKey, 7, TimeUnit.DAYS);

        } catch (Exception e) {
            logger.error("记录详细统计异常: " + ruleId + " - " + dimension + ":" + dimensionValue, e);
        }
    }

    /**
     * 记录实时统计数据（按分钟聚合）
     */
    private void recordRealtimeStats(String ruleId, boolean allowed) {
        try {
            // 按分钟聚合统计数据
            long currentMinute = System.currentTimeMillis() / (60 * 1000) * (60 * 1000);
            String realtimeKey = "rate_limit:realtime:" + ruleId + ":" + currentMinute;

            redisTemplate.opsForHash().increment(realtimeKey, "requests", 1);
            if (allowed) {
                redisTemplate.opsForHash().increment(realtimeKey, "allowed", 1);
            } else {
                redisTemplate.opsForHash().increment(realtimeKey, "blocked", 1);
            }

            // 设置过期时间（24小时）
            redisTemplate.expire(realtimeKey, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error("记录实时统计异常: " + ruleId, e);
        }
    }

    @Override
    public List<DetailedRateLimitStats> getDetailedStats(String ruleId) {
        List<DetailedRateLimitStats> detailedStats = new ArrayList<>();

        try {
            // 获取IP统计
            detailedStats.addAll(getIpStats(ruleId, 100));

            // 获取用户统计
            detailedStats.addAll(getUserStats(ruleId, 100));

        } catch (Exception e) {
            logger.error("获取详细统计信息异常: " + ruleId, e);
        }

        return detailedStats;
    }

    @Override
    public List<DetailedRateLimitStats> getIpStats(String ruleId, int limit) {
        return getDimensionStats(ruleId, "ip", limit);
    }

    @Override
    public List<DetailedRateLimitStats> getUserStats(String ruleId, int limit) {
        return getDimensionStats(ruleId, "user", limit);
    }

    /**
     * 获取指定维度的统计信息
     */
    private List<DetailedRateLimitStats> getDimensionStats(String ruleId, String dimension, int limit) {
        List<DetailedRateLimitStats> statsList = new ArrayList<>();

        try {
            // 获取规则信息
            RateLimitRule rule = configService.getRule(ruleId);
            String ruleName = rule != null ? rule.getName() : "未知规则";

            // 获取维度列表（按请求数排序）
            String dimensionListKey = redisKeyGenerator.generateDimensionListKey(ruleId, dimension);
            Set<Object> dimensionValues = redisTemplate.opsForZSet().reverseRange(dimensionListKey, 0, limit - 1);

            if (dimensionValues != null) {
                for (Object dimensionValue : dimensionValues) {
                    String value = dimensionValue.toString();
                    String detailedStatsKey = redisKeyGenerator.generateDetailedStatsKey(ruleId, dimension, value);
                    Map<Object, Object> statsData = redisTemplate.opsForHash().entries(detailedStatsKey);

                    if (!statsData.isEmpty()) {
                        DetailedRateLimitStats stats = new DetailedRateLimitStats(ruleId, ruleName, dimension, value);
                        stats.setTotalRequests(getLongValue(statsData, "totalRequests"));
                        stats.setAllowedRequests(getLongValue(statsData, "allowedRequests"));
                        stats.setBlockedRequests(getLongValue(statsData, "blockedRequests"));
                        stats.setLastRequestTime(getLongValue(statsData, "lastRequestTime"));

                        // 计算请求频率和阻止率
                        stats.calculateRequestRate();
                        stats.calculateBlockRate();

                        statsList.add(stats);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("获取维度统计信息异常: " + ruleId + " - " + dimension, e);
        }

        return statsList;
    }

    @Override
    public void recordRateLimitDetail(RateLimitRecord record) {
        try {
            if (record.getId() == null) {
                record.setId(UUID.randomUUID().toString());
            }

            // 将记录存储到Redis
            String recordKey = "rate_limit:records:" + record.getRuleId() + ":" + record.getId();
            String recordJson = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(recordKey, recordJson, 24, TimeUnit.HOURS);

            // 添加到时间序列索引（用于按时间查询）
            String timeIndexKey = "rate_limit:time_index:" + record.getRuleId();
            redisTemplate.opsForZSet().add(timeIndexKey, record.getId(), record.getRequestTime());
            redisTemplate.expire(timeIndexKey, 24, TimeUnit.HOURS);

            // 添加到全局时间索引
            String globalTimeIndexKey = "rate_limit:global_time_index";
            redisTemplate.opsForZSet().add(globalTimeIndexKey, record.getId(), record.getRequestTime());
            redisTemplate.expire(globalTimeIndexKey, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            logger.error("记录限流详细信息异常: " + record.getRuleId(), e);
        }
    }

    @Override
    public List<RateLimitRecord> getRateLimitRecords(String ruleId, int limit) {
        List<RateLimitRecord> records = new ArrayList<>();
        try {
            String timeIndexKey = "rate_limit:time_index:" + ruleId;
            Set<Object> recordIds = redisTemplate.opsForZSet().reverseRange(timeIndexKey, 0, limit - 1);

            if (recordIds != null) {
                for (Object recordId : recordIds) {
                    String recordKey = "rate_limit:records:" + ruleId + ":" + recordId.toString();
                    String recordJson = (String) redisTemplate.opsForValue().get(recordKey);
                    if (recordJson != null) {
                        RateLimitRecord record = objectMapper.readValue(recordJson, RateLimitRecord.class);
                        records.add(record);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取限流记录异常: " + ruleId, e);
        }
        return records;
    }

    @Override
    public Map<String, Object> getTrendData(int minutes) {
        Map<String, Object> trendData = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> totalData = new ArrayList<>();
        List<Long> blockedData = new ArrayList<>();

        try {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (minutes * 60 * 1000L);

            // 根据时间范围决定间隔
            int intervalMinutes;
            int maxDataPoints;

            if (minutes <= 15) {
                // 15分钟及以下：每分钟一个数据点
                intervalMinutes = 1;
                maxDataPoints = minutes;
            } else if (minutes <= 60) {
                // 1小时及以下：每5分钟一个数据点
                intervalMinutes = 5;
                maxDataPoints = minutes / 5;
            } else if (minutes <= 360) {
                // 6小时及以下：每15分钟一个数据点
                intervalMinutes = 15;
                maxDataPoints = minutes / 15;
            } else {
                // 更长时间：每小时一个数据点
                intervalMinutes = 60;
                maxDataPoints = minutes / 60;
            }

            // 确保至少有一个数据点
            if (maxDataPoints == 0) maxDataPoints = 1;

            long intervalMs = intervalMinutes * 60 * 1000L;

            // 从最早的时间点开始，按间隔生成数据点
            for (int i = 0; i < maxDataPoints; i++) {
                long timePoint = startTime + (i * intervalMs);

                // 格式化时间标签
                String label = new java.text.SimpleDateFormat("HH:mm")
                    .format(new java.util.Date(timePoint));
                labels.add(label);

                // 聚合这个时间段的数据
                long totalRequests = 0;
                long blockedRequests = 0;

                // 查询所有规则在这个时间段的实时统计数据
                List<RateLimitRule> rules = configService.getAllRules();
                for (RateLimitRule rule : rules) {
                    if (intervalMinutes == 1) {
                        // 分钟级别：直接查询对应分钟的数据
                        long timeMinute = timePoint / (60 * 1000) * (60 * 1000);
                        String realtimeKey = redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:realtime:" + rule.getId() + ":" + timeMinute;

                        Map<Object, Object> data = redisTemplate.opsForHash().entries(realtimeKey);
                        if (!data.isEmpty()) {
                            totalRequests += getLongValue(data, "requests");
                            blockedRequests += getLongValue(data, "blocked");
                        }
                    } else {
                        // 多分钟间隔：聚合这个时间段内所有分钟的数据
                        for (int j = 0; j < intervalMinutes; j++) {
                            long minuteTime = timePoint + (j * 60 * 1000);
                            long timeMinute = minuteTime / (60 * 1000) * (60 * 1000);
                            String realtimeKey = redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:realtime:" + rule.getId() + ":" + timeMinute;

                            Map<Object, Object> data = redisTemplate.opsForHash().entries(realtimeKey);
                            if (!data.isEmpty()) {
                                totalRequests += getLongValue(data, "requests");
                                blockedRequests += getLongValue(data, "blocked");
                            }
                        }
                    }
                }

                totalData.add(totalRequests);
                blockedData.add(blockedRequests);
            }

            trendData.put("labels", labels);
            trendData.put("totalData", totalData);
            trendData.put("blockedData", blockedData);
            trendData.put("intervalMinutes", intervalMinutes);
            trendData.put("dataPoints", maxDataPoints);

        } catch (Exception e) {
            logger.error("获取趋势数据异常", e);
            // 返回空数据
            trendData.put("labels", new ArrayList<>());
            trendData.put("totalData", new ArrayList<>());
            trendData.put("blockedData", new ArrayList<>());
            trendData.put("intervalMinutes", 1);
            trendData.put("dataPoints", 0);
        }

        return trendData;
    }

    @Override
    public Map<String, Object> getTrendData(String ruleId, int minutes) {
        Map<String, Object> trendData = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> totalData = new ArrayList<>();
        List<Long> blockedData = new ArrayList<>();

        try {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (minutes * 60 * 1000L);

            // 根据时间范围决定间隔
            int intervalMinutes;
            int maxDataPoints;

            if (minutes <= 15) {
                // 15分钟及以下：每分钟一个数据点
                intervalMinutes = 1;
                maxDataPoints = minutes;
            } else if (minutes <= 60) {
                // 1小时及以下：每5分钟一个数据点
                intervalMinutes = 5;
                maxDataPoints = minutes / 5;
            } else if (minutes <= 360) {
                // 6小时及以下：每15分钟一个数据点
                intervalMinutes = 15;
                maxDataPoints = minutes / 15;
            } else {
                // 更长时间：每小时一个数据点
                intervalMinutes = 60;
                maxDataPoints = minutes / 60;
            }

            // 确保至少有一个数据点
            if (maxDataPoints == 0) maxDataPoints = 1;

            long intervalMs = intervalMinutes * 60 * 1000L;

            // 从最早的时间点开始，按间隔生成数据点
            for (int i = 0; i < maxDataPoints; i++) {
                long timePoint = startTime + (i * intervalMs);

                // 格式化时间标签
                String label = new java.text.SimpleDateFormat("HH:mm")
                        .format(new java.util.Date(timePoint));
                labels.add(label);

                // 聚合这个时间段的数据
                long totalRequests = 0;
                long blockedRequests = 0;

                if (intervalMinutes == 1) {
                    // 分钟级别：直接查询对应分钟的数据
                    long timeMinute = timePoint / (60 * 1000) * (60 * 1000);
                    String realtimeKey = redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:realtime:" + ruleId + ":" + timeMinute;

                    Map<Object, Object> data = redisTemplate.opsForHash().entries(realtimeKey);
                    if (!data.isEmpty()) {
                        totalRequests += getLongValue(data, "requests");
                        blockedRequests += getLongValue(data, "blocked");
                    }
                } else {
                    // 多分钟间隔：聚合这个时间段内所有分钟的数据
                    for (int j = 0; j < intervalMinutes; j++) {
                        long minuteTime = timePoint + (j * 60 * 1000);
                        long timeMinute = minuteTime / (60 * 1000) * (60 * 1000);
                        String realtimeKey = redisKeyGenerator.getRedisKeyPrefix()+":"+"rate_limit:realtime:" + ruleId + ":" + timeMinute;

                        Map<Object, Object> data = redisTemplate.opsForHash().entries(realtimeKey);
                        if (!data.isEmpty()) {
                            totalRequests += getLongValue(data, "requests");
                            blockedRequests += getLongValue(data, "blocked");
                        }
                    }
                }

                totalData.add(totalRequests);
                blockedData.add(blockedRequests);
            }

            trendData.put("labels", labels);
            trendData.put("totalData", totalData);
            trendData.put("blockedData", blockedData);
            trendData.put("intervalMinutes", intervalMinutes);
            trendData.put("dataPoints", maxDataPoints);

        } catch (Exception e) {
            logger.error("获取趋势数据异常", e);
            // 返回空数据
            trendData.put("labels", new ArrayList<>());
            trendData.put("totalData", new ArrayList<>());
            trendData.put("blockedData", new ArrayList<>());
            trendData.put("intervalMinutes", 1);
            trendData.put("dataPoints", 0);
        }

        return trendData;
    }

    @Override
    public List<RateLimitRecord> getRecentRateLimitRecords(int minutes, int limit) {
        List<RateLimitRecord> records = new ArrayList<>();
        try {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (minutes * 60 * 1000L);

            String globalTimeIndexKey =redisKeyGenerator.generateGlobalTimeIndexKey();
            Set<Object> recordIds = redisTemplate.opsForZSet().reverseRangeByScore(globalTimeIndexKey, startTime, endTime, 0, limit);

            if (recordIds != null) {
                for (Object recordId : recordIds) {
                    // 需要找到对应的规则ID
                    Set<String> keys = redisTemplate.keys(redisKeyGenerator.getRedisKeyPrefix() + ":" + "rate_limit:records:*:" + recordId.toString());
                    for (String key : keys) {
                        String recordJson = (String) redisTemplate.opsForValue().get(key);
                        if (recordJson != null) {
                            RateLimitRecord record = objectMapper.readValue(recordJson, RateLimitRecord.class);
                            records.add(record);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取最近限流记录异常", e);
        }
        return records;
    }

    /**
     * 从HTTP请求记录详细的限流信息
     */
    private void recordRateLimitDetailFromRequest(HttpServletRequest request, RateLimitRule rule, boolean allowed) {
        try {
            RateLimitRecord record = new RateLimitRecord(rule.getId(), rule.getName());
            record.setRequestPath(request.getRequestURI());
            record.setHttpMethod(request.getMethod());
            record.setClientIp(getClientIp(request));
            record.setUserId(getUserId(request));
            record.setBlocked(!allowed);

            // 设置User-Agent（截取前200个字符）
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 200) {
                userAgent = userAgent.substring(0, 200);
            }
            record.setUserAgent(userAgent);

            // 设置Referer
            record.setReferer(request.getHeader("Referer"));

            // 如果被阻止，尝试确定阻止原因
            if (!allowed) {
                record.setBlockReason(determineBlockReason(request, rule));
            }

            recordRateLimitDetail(record);
        } catch (Exception e) {
            logger.error("记录请求详细信息异常", e);
        }
    }

    /**
     * 确定阻止原因
     */
    private String determineBlockReason(HttpServletRequest request, RateLimitRule rule) {
        // 这里可以根据具体的限流检查逻辑来确定阻止原因
        // 由于我们无法在这里重新执行限流检查，所以使用一个简化的逻辑
        if (rule.isEnableIpLimit() && rule.isEnableUserLimit()) {
            return "多维度限流";
        } else if (rule.isEnableIpLimit()) {
            return "IP限流";
        } else if (rule.isEnableUserLimit()) {
            return "用户限流";
        } else {
            return "路径限流";
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * 获取用户ID
     */
    private String getUserId(HttpServletRequest request) {
        String userId = request.getParameter("userId");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }

        userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }

        if (request.getSession(false) != null) {
            Object sessionUserId = request.getSession(false).getAttribute("userId");
            if (sessionUserId != null) {
                return sessionUserId.toString();
            }
        }

        return null;
    }

    /**
     * 从Map中获取Long值
     */
    private long getLongValue(Map<Object, Object> map, String key) {
        Object value = map.get(key);
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
