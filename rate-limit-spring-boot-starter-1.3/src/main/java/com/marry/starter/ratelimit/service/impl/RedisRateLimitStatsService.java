package com.marry.starter.ratelimit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marry.starter.ratelimit.autoconfigure.RateLimitProperties;
import com.marry.starter.ratelimit.model.RateLimitRule;
import com.marry.starter.ratelimit.model.RateLimitStats;
import com.marry.starter.ratelimit.model.RateLimitRecord;
import com.marry.starter.ratelimit.service.RateLimitConfigService;
import com.marry.starter.ratelimit.service.RateLimitStatsService;
import com.marry.starter.ratelimit.strategy.impl.IpRateLimitStrategy;
import com.marry.starter.ratelimit.strategy.impl.UserRateLimitStrategy;
import com.marry.starter.ratelimit.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final RedisKeyGenerator redisKeyGenerator;
    private final RateLimitProperties properties;

    public RedisRateLimitStatsService(RedisTemplate<String, Object> redisTemplate,
                                      RateLimitConfigService configService,
                                      IpRateLimitStrategy ipStrategy,
                                      UserRateLimitStrategy userStrategy,
                                      RedisKeyGenerator redisKeyGenerator, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.ipStrategy = ipStrategy;
        this.userStrategy = userStrategy;
        this.redisKeyGenerator = redisKeyGenerator;
        this.properties = properties;
    }

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

            if (properties.getStats().isEnableDetail()) {
                // 记录详细的限流记录
                recordRateLimitDetailFromRequest(request, rule, allowed);
            }

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
    public void recordRateLimitDetail(RateLimitRecord record) {
        try {
            if (record.getId() == null) {
                record.setId(UUID.randomUUID().toString());
            }

            // 将记录存储到Redis
            String recordKey = redisKeyGenerator.generateRecordKey(record.getRuleId(), record.getId());
            String recordJson = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(recordKey, recordJson, 24, TimeUnit.HOURS);

            // 添加到时间序列索引（用于按时间查询）
            String timeIndexKey =redisKeyGenerator.generateTimeIndexKey(record.getRuleId());
            redisTemplate.opsForZSet().add(timeIndexKey, record.getId(), record.getRequestTime());
            redisTemplate.expire(timeIndexKey, 24, TimeUnit.HOURS);

            // 添加到全局时间索引
            String globalTimeIndexKey = redisKeyGenerator.generateGlobalTimeIndexKey();
            redisTemplate.opsForZSet().add(globalTimeIndexKey, record.getId(), record.getRequestTime());
            redisTemplate.expire(globalTimeIndexKey, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            logger.error("记录限流详细信息异常: " + record.getRuleId(), e);
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
            String realtimeKey = redisKeyGenerator.generateRealtimeKey(ruleId, currentMinute);

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
