package io.github.jicklin.starter.ratelimit.service.impl;

import io.github.jicklin.starter.ratelimit.autoconfigure.RateLimitProperties;
import io.github.jicklin.starter.ratelimit.model.RateLimitRecord;
import io.github.jicklin.starter.ratelimit.model.RateLimitRule;

import io.github.jicklin.starter.ratelimit.service.RateLimitConfigService;
import io.github.jicklin.starter.ratelimit.service.RateLimitStatsService;
import io.github.jicklin.starter.ratelimit.strategy.impl.IpRateLimitStrategy;
import io.github.jicklin.starter.ratelimit.strategy.impl.UserRateLimitStrategy;
import io.github.jicklin.starter.ratelimit.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 优化的限流统计服务实现
 * 专门处理大量用户参与的场景，减少Redis键数量
 */
public class OptimizedRateLimitStatsService implements RateLimitStatsService {

    private static final Logger logger = LoggerFactory.getLogger(OptimizedRateLimitStatsService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimitConfigService configService;
    private final IpRateLimitStrategy ipStrategy;
    private final UserRateLimitStrategy userStrategy;
    private final RedisKeyGenerator keyGenerator;
    private final RateLimitProperties properties;

    public OptimizedRateLimitStatsService(RedisTemplate<String, Object> redisTemplate,
                                        RateLimitConfigService configService,
                                        IpRateLimitStrategy ipStrategy,
                                        UserRateLimitStrategy userStrategy,
                                        RedisKeyGenerator keyGenerator,
                                        RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.ipStrategy = ipStrategy;
        this.userStrategy = userStrategy;
        this.keyGenerator = keyGenerator;
        this.properties = properties;
    }

    @Override
    public void recordRequest(String ruleId, boolean allowed) {
        try {
            String statsKey = keyGenerator.generateStatsKey(ruleId);

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

    /**
     * 记录实时统计数据（按分钟聚合）
     */
    private void recordRealtimeStats(String ruleId, boolean allowed) {
        try {
            // 按分钟聚合统计数据
            long currentMinute = System.currentTimeMillis() / (60 * 1000) * (60 * 1000);
            String realtimeKey = keyGenerator.generateRealtimeKey(ruleId, currentMinute);

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
    public void recordRequest(HttpServletRequest request, String ruleId, boolean allowed) {
        // 先记录基础统计
        recordRequest(ruleId, allowed);

        try {
           /* RateLimitRule rule = configService.getRule(ruleId);
            if (rule == null) {
                return;
            }*/

            // 智能选择统计策略
            String ip = ipStrategy.extractIdentifier(request);
            if (ip != null) {
                recordOptimizedStats(ruleId, "ip", ip, allowed);
            }


            String userId = userStrategy.extractIdentifier(request);
            if (userId != null) {
                recordOptimizedStats(ruleId, "user", userId, allowed);
            }


        } catch (Exception e) {
            logger.error("记录详细统计异常: " + ruleId, e);
        }
    }

    /**
     * 优化的统计记录方法
     * 直接使用采样和热点统计，不再判断数量
     */
    private void recordOptimizedStats(String ruleId, String dimension, String dimensionValue, boolean allowed) {
        try {

            recordHotspotStats(ruleId, dimension, dimensionValue, allowed);

        } catch (Exception e) {
            logger.error("记录优化统计异常: " + ruleId + ":" + dimension + ":" + dimensionValue, e);
        }
    }



    /**
     * 使用采样和热点统计
     */
    private void recordSampledAndHotspotStats(String ruleId, String dimension, String dimensionValue, boolean allowed) {
        // 获取配置中的采样率
        int sampleRate = properties.getStats().getSampleRate();

        // 采样统计：只记录部分请求
        if (ThreadLocalRandom.current().nextInt(sampleRate) == 0) {
            String sampledKey = keyGenerator.generateSampledStatsKey(ruleId, dimension, sampleRate);
            redisTemplate.opsForHash().increment(sampledKey, "totalSamples", 1);
            if (allowed) {
                redisTemplate.opsForHash().increment(sampledKey, "allowedSamples", 1);
            } else {
                redisTemplate.opsForHash().increment(sampledKey, "blockedSamples", 1);
            }
            redisTemplate.expire(sampledKey, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
        }

        // 热点统计：记录访问频率高的IP/用户
        String hotspotKey = keyGenerator.generateHotspotStatsKey(ruleId, dimension);
        redisTemplate.opsForZSet().incrementScore(hotspotKey, dimensionValue, 1);

        // 获取配置中的热点统计保留数量
        int hotspotTopN = properties.getStats().getHotspotTopN();

        // 只保留Top N
        redisTemplate.opsForZSet().removeRange(hotspotKey, 0, -hotspotTopN - 1);
        redisTemplate.expire(hotspotKey, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
    }


    /**
     * 使用采样和热点统计
     */
    private void recordHotspotStats(String ruleId, String dimension, String dimensionValue, boolean allowed) {

        // 热点统计：记录访问频率高的IP/用户
        String hotspotKey = keyGenerator.generateHotspotStatsKey(ruleId, dimension);
        redisTemplate.opsForZSet().incrementScore(hotspotKey, dimensionValue, 1);

        // 获取配置中的热点统计保留数量
        int hotspotTopN = properties.getStats().getHotspotTopN();

        // 只保留Top N
        redisTemplate.opsForZSet().removeRange(hotspotKey, 0, -hotspotTopN - 1);
        redisTemplate.expire(hotspotKey, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * 记录聚合统计
     */
    private void recordAggregatedStats(String ruleId, String dimension, boolean allowed) {
        // 获取配置中的聚合窗口大小
        int aggregationWindow = properties.getStats().getAggregationWindowMinutes();

        String aggKey = keyGenerator.generateAggregatedStatsKey(ruleId, dimension, aggregationWindow);

        redisTemplate.opsForHash().increment(aggKey, "totalRequests", 1);
        if (allowed) {
            redisTemplate.opsForHash().increment(aggKey, "allowedRequests", 1);
        } else {
            redisTemplate.opsForHash().increment(aggKey, "blockedRequests", 1);
        }

        // 聚合数据保留时间较短
        redisTemplate.expire(aggKey, 2 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
    }







    @Override
    public void recordRateLimitDetail(RateLimitRecord record) {

    }


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
