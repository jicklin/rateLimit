package io.github.jicklin.starter.ratelimit.service.impl;

import io.github.jicklin.starter.ratelimit.model.HttpMethod;
import io.github.jicklin.starter.ratelimit.model.RateLimitRule;
import io.github.jicklin.starter.ratelimit.service.RateLimitConfigService;
import io.github.jicklin.starter.ratelimit.service.RateLimitService;
import io.github.jicklin.starter.ratelimit.service.RateLimitStatsService;
import io.github.jicklin.starter.ratelimit.strategy.RateLimitStrategy;
import io.github.jicklin.starter.ratelimit.strategy.RateLimitStrategyFactory;
import io.github.jicklin.starter.ratelimit.util.AntPathMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 基于Redis的限流服务实现
 */
public class RedisRateLimitService implements RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimitConfigService configService;
    private final RateLimitStatsService statsService;
    private final RateLimitStrategyFactory strategyFactory;

    /**
     * Lua脚本：令牌桶算法实现
     * 修复多次填充问题：确保同一时间点不会重复填充令牌
     */
    private static final String TOKEN_BUCKET_SCRIPT =
        "local key = KEYS[1]\n" +
        "local capacity = tonumber(ARGV[1])\n" +
        "local refill_rate = tonumber(ARGV[2])\n" +
        "local time_window = tonumber(ARGV[3])\n" +
        "local now = tonumber(ARGV[4])\n" +
        "\n" +
        "local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')\n" +
        "local tokens = tonumber(bucket[1]) or capacity\n" +
        "local last_refill = tonumber(bucket[2]) or now\n" +
        "\n" +
        "-- 计算需要补充的令牌数（修复：确保时间间隔计算正确）\n" +
        "local elapsed = math.max(0, now - last_refill)\n" +
        "-- refill_rate是每秒补充的令牌数，elapsed是秒数\n" +
        "local tokens_to_add = math.floor(elapsed * refill_rate)\n" +
        "\n" +
        "-- 只有当需要添加令牌时才更新last_refill，避免重复填充\n" +
        "local new_last_refill = last_refill\n" +
        "if tokens_to_add > 0 then\n" +
        "    tokens = math.min(capacity, tokens + tokens_to_add)\n" +
        "    new_last_refill = now\n" +
        "end\n" +
        "\n" +
        "local allowed = 0\n" +
        "if tokens > 0 then\n" +
        "    tokens = tokens - 1\n" +
        "    allowed = 1\n" +
        "end\n" +
        "\n" +
        "-- 更新令牌桶状态\n" +
        "redis.call('HMSET', key, 'tokens', tokens, 'last_refill', new_last_refill)\n" +
        "redis.call('EXPIRE', key, time_window * 2)\n" +
        "return {allowed, tokens}";

    private final DefaultRedisScript<List> tokenBucketScript;

    public RedisRateLimitService(RedisTemplate<String, Object> redisTemplate,
                               RateLimitConfigService configService,
                               RateLimitStatsService statsService,
                               RateLimitStrategyFactory strategyFactory) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.statsService = statsService;
        this.strategyFactory = strategyFactory;

        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(TOKEN_BUCKET_SCRIPT);
        this.tokenBucketScript.setResultType(List.class);
    }

    @Override
    public boolean isAllowed(HttpServletRequest request) {
        try {
            List<RateLimitRule> rules = configService.getEnabledRules();

            for (RateLimitRule rule : rules) {
                if (matchesRule(request, rule)) {
                    // 检查多维度限流
                    boolean allowed = checkMultiDimensionRateLimit(request, rule);

                    // 记录统计信息（带请求上下文，支持IP和用户维度统计）
                    statsService.recordRequest(request, rule.getId(), allowed);

                    if (!allowed) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("限流检查异常", e);
            // 异常情况下允许请求通过，避免影响业务
            return true;
        }
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, RateLimitRule rule) {
        if (rule == null || !rule.isEnabled()) {
            return true;
        }

        try {
            // 默认使用路径限流策略
            RateLimitStrategy pathStrategy = strategyFactory.getStrategy(rule);
            String pathKey = pathStrategy.generateKey(request, rule);

            // 检查路径限流
            boolean pathAllowed = checkTokenBucket(pathKey, rule.getBucketCapacity(), rule.getRefillRate(), rule.getTimeWindow());

            if (!pathAllowed) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("限流检查异常: " + rule.getName(), e);
            return true; // 异常情况下允许通过
        }
    }

    /**
     * 检查多维度限流
     */
    private boolean checkMultiDimensionRateLimit(HttpServletRequest request, RateLimitRule rule) {
        // 1. 检查路径限流（默认维度）
        if (!checkPathRateLimit(request, rule)) {
            return false;
        }

        // 2. 检查IP限流（如果启用）
        if (rule.isEnableIpLimit() && !checkIpRateLimit(request, rule)) {
            return false;
        }

        // 3. 检查用户限流（如果启用）
        if (rule.isEnableUserLimit() && !checkUserRateLimit(request, rule)) {
            return false;
        }

        return true;
    }

    /**
     * 检查路径限流
     */
    private boolean checkPathRateLimit(HttpServletRequest request, RateLimitRule rule) {
        RateLimitStrategy pathStrategy = strategyFactory.getStrategy(rule);
        String pathKey = pathStrategy.generateKey(request, rule);

        return checkTokenBucket(pathKey, rule.getBucketCapacity(), rule.getRefillRate(), rule.getTimeWindow());
    }

    /**
     * 检查IP限流
     */
    private boolean checkIpRateLimit(HttpServletRequest request, RateLimitRule rule) {
        RateLimitStrategy ipStrategy = strategyFactory.getIpStrategy(rule);
        if (ipStrategy == null) {
            return true;
        }

        String ip = ipStrategy.extractIdentifier(request);
        if (ip == null) {
            return true;
        }

        String ipKey = ipStrategy.generateKey(request, rule);
        int capacity = rule.getIpBucketCapacity() != null ? rule.getIpBucketCapacity() : rule.getBucketCapacity();
        int rate = rule.getIpRequestLimit() != null ? rule.getIpRequestLimit() : rule.getRefillRate();

        return checkTokenBucket(ipKey, capacity, rate, rule.getTimeWindow());
    }

    /**
     * 检查用户限流
     */
    private boolean checkUserRateLimit(HttpServletRequest request, RateLimitRule rule) {
        RateLimitStrategy userStrategy = strategyFactory.getUserStrategy(rule);
        if (userStrategy == null) {
            return true;
        }

        String userId = userStrategy.extractIdentifier(request);
        if (userId == null) {
            // 如果没有用户ID，使用IP作为fallback
//            return checkIpRateLimit(request, rule);
            return true;
        }

        String userKey = userStrategy.generateKey(request, rule);
        int capacity = rule.getUserBucketCapacity() != null ? rule.getUserBucketCapacity() : rule.getBucketCapacity();
        int rate = rule.getUserRequestLimit() != null ? rule.getUserRequestLimit() : rule.getRefillRate();

        return checkTokenBucket(userKey, capacity, rate, rule.getTimeWindow());
    }

    /**
     * 使用Lua脚本检查令牌桶
     */
    private boolean checkTokenBucket(String key, int capacity, int refillRate, int timeWindow) {
        try {
            long now = Instant.now().getEpochSecond();

            List<String> keys = Arrays.asList(key);
            Object[] args = {capacity, refillRate, timeWindow, now};

            logger.debug("执行令牌桶检查: key={}, capacity={}, refillRate={}, timeWindow={}, now={}",
                key, capacity, refillRate, timeWindow, now);

            List result = redisTemplate.execute(tokenBucketScript, keys, args);
            if (result != null && result.size() >= 2) {
                Number allowed = (Number) result.get(0);
                Number tokens = (Number) result.get(1);
                boolean isAllowed = allowed.intValue() == 1;

                logger.debug("令牌桶检查结果: key={}, allowed={}, remainingTokens={}, now={}, timestamp={}",
                        key, isAllowed, tokens, now, System.currentTimeMillis());

                return isAllowed;
            }

            logger.warn("令牌桶脚本返回结果异常: key={}, result={}", key, result);
            return false;
        } catch (Exception e) {
            logger.error("令牌桶检查异常: " + key, e);
            // 临时修改：异常情况下拒绝请求，用于调试
            return false; // 异常情况下拒绝通过
        }
    }

    /**
     * 检查请求是否匹配规则
     */
    private boolean matchesRule(HttpServletRequest request, RateLimitRule rule) {
        // 检查路径模式
        String requestPath = request.getRequestURI();
        if (!AntPathMatcher.match(rule.getPathPattern(), requestPath)) {
            return false;
        }

        // 检查HTTP方法
        if (rule.getHttpMethods() != null && !rule.getHttpMethods().isEmpty()) {
            String requestMethod = request.getMethod();
            HttpMethod httpMethod = HttpMethod.fromString(requestMethod);
            if (httpMethod == null || !rule.getHttpMethods().contains(httpMethod)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public long getRemainingTokens(HttpServletRequest request, RateLimitRule rule) {
        if (rule == null || !rule.isEnabled()) {
            return rule != null ? rule.getBucketCapacity() : 0;
        }

        try {
            RateLimitStrategy strategy = strategyFactory.getStrategy(rule);
            String key = strategy.generateKey(request, rule);

            Object tokens = redisTemplate.opsForHash().get(key, "tokens");
            return tokens != null ? Long.parseLong(tokens.toString()) : rule.getBucketCapacity();
        } catch (Exception e) {
            logger.error("获取剩余令牌数异常: " + rule.getName(), e);
            return rule.getBucketCapacity();
        }
    }

    @Override
    public void reset(HttpServletRequest request, RateLimitRule rule) {
        if (rule == null) {
            return;
        }

        try {
            RateLimitStrategy strategy = strategyFactory.getStrategy(rule);
            String key = strategy.generateKey(request, rule);
            redisTemplate.delete(key);

            logger.info("重置限流状态: {} - {}", rule.getName(), key);
        } catch (Exception e) {
            logger.error("重置限流状态异常: " + rule.getName(), e);
        }
    }

    @Override
    public void resetAll() {
        try {
            // 删除所有限流相关的键
            redisTemplate.delete(redisTemplate.keys("rate_limit:bucket:*"));
            logger.info("重置所有限流状态");
        } catch (Exception e) {
            logger.error("重置所有限流状态异常", e);
        }
    }
}
