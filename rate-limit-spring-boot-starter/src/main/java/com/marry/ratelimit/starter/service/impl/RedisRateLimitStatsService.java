package com.marry.ratelimit.starter.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marry.ratelimit.starter.model.RateLimitRule;
import com.marry.ratelimit.starter.model.RateLimitStats;
import com.marry.ratelimit.starter.service.RateLimitConfigService;
import com.marry.ratelimit.starter.service.RateLimitStatsService;
import com.marry.ratelimit.starter.strategy.impl.IpRateLimitStrategy;
import com.marry.ratelimit.starter.strategy.impl.UserRateLimitStrategy;
import com.marry.ratelimit.starter.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 基于Redis的限流统计服务实现
 */
public class RedisRateLimitStatsService implements RateLimitStatsService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitStatsService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimitConfigService configService;
    private final IpRateLimitStrategy ipStrategy;
    private final UserRateLimitStrategy userStrategy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisRateLimitStatsService(RedisTemplate<String, Object> redisTemplate,
                                    RateLimitConfigService configService,
                                    IpRateLimitStrategy ipStrategy,
                                    UserRateLimitStrategy userStrategy) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.ipStrategy = ipStrategy;
        this.userStrategy = userStrategy;
    }
    
    @Override
    public void recordRequest(String ruleId, boolean allowed) {
        try {
            String key = RedisKeyGenerator.generateStatsKey(ruleId);
            
            // 增加总请求数
            redisTemplate.opsForHash().increment(key, "totalRequests", 1);
            
            if (allowed) {
                // 增加允许请求数
                redisTemplate.opsForHash().increment(key, "allowedRequests", 1);
            } else {
                // 增加阻止请求数
                redisTemplate.opsForHash().increment(key, "blockedRequests", 1);
            }
            
            // 更新最后请求时间
            redisTemplate.opsForHash().put(key, "lastRequestTime", System.currentTimeMillis());
            
            // 设置过期时间（24小时）
            redisTemplate.expire(key, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
            
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

        } catch (Exception e) {
            logger.error("记录详细请求统计异常: " + ruleId, e);
        }
    }
    
    /**
     * 记录详细统计信息（IP和用户维度）
     */
    private void recordDetailedStats(String ruleId, String dimension, String dimensionValue, boolean allowed) {
        try {
            String key = RedisKeyGenerator.generateDetailedStatsKey(ruleId, dimension, dimensionValue);
            
            // 增加总请求数
            redisTemplate.opsForHash().increment(key, "totalRequests", 1);
            
            if (allowed) {
                redisTemplate.opsForHash().increment(key, "allowedRequests", 1);
            } else {
                redisTemplate.opsForHash().increment(key, "blockedRequests", 1);
            }
            
            // 更新最后请求时间
            redisTemplate.opsForHash().put(key, "lastRequestTime", System.currentTimeMillis());
            
            // 设置过期时间
            redisTemplate.expire(key, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
            
            // 将维度值添加到列表中（用于后续查询）
            String listKey = RedisKeyGenerator.generateDimensionListKey(ruleId, dimension);
            redisTemplate.opsForSet().add(listKey, dimensionValue);
            redisTemplate.expire(listKey, 24 * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
            
        } catch (Exception e) {
            logger.error("记录详细统计信息异常: " + ruleId + ":" + dimension + ":" + dimensionValue, e);
        }
    }
    
    @Override
    public RateLimitStats getStats(String ruleId) {
        try {
            String key = RedisKeyGenerator.generateStatsKey(ruleId);
            Map<Object, Object> statsData = redisTemplate.opsForHash().entries(key);
            
            RateLimitRule rule = configService.getRule(ruleId);
            String ruleName = rule != null ? rule.getName() : "未知规则";
            
            RateLimitStats stats = new RateLimitStats(ruleId, ruleName);
            
            if (!statsData.isEmpty()) {
                stats.setTotalRequests(getLongValue(statsData.get("totalRequests")));
                stats.setAllowedRequests(getLongValue(statsData.get("allowedRequests")));
                stats.setBlockedRequests(getLongValue(statsData.get("blockedRequests")));
                stats.setLastRequestTime(getLongValue(statsData.get("lastRequestTime")));
                
                // 计算统计指标
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
            String key = RedisKeyGenerator.generateStatsKey(ruleId);
            redisTemplate.delete(key);
            
            logger.info("重置统计信息: {}", ruleId);
        } catch (Exception e) {
            logger.error("重置统计信息异常: " + ruleId, e);
        }
    }
    
    @Override
    public void resetAllStats() {
        try {
            // 删除所有统计数据
            redisTemplate.delete(redisTemplate.keys("rate_limit:stats:*"));
            redisTemplate.delete(redisTemplate.keys("rate_limit:realtime:*"));
            redisTemplate.delete(redisTemplate.keys("rate_limit:detailed_stats:*"));
            redisTemplate.delete(redisTemplate.keys("rate_limit:dimension_list:*"));

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
        // 简化实现，返回基础统计
        return getStats(ruleId);
    }
    
    @Override
    public Map<String, Object> getTrendData(int minutes) {
        // 简化实现，返回空数据
        Map<String, Object> trendData = new HashMap<>();
        trendData.put("labels", new ArrayList<>());
        trendData.put("requests", new ArrayList<>());
        trendData.put("blocked", new ArrayList<>());
        return trendData;
    }
    
    @Override
    public Map<String, Object> getTrendData(String ruleId, int minutes) {
        // 简化实现，返回空数据
        return getTrendData(minutes);
    }
    
    /**
     * 安全地获取Long值
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
