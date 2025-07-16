package com.marry.ratelimit.service.impl;

import com.marry.ratelimit.model.HttpMethod;
import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.service.RateLimitConfigService;
import com.marry.ratelimit.service.RateLimitService;
import com.marry.ratelimit.service.RateLimitStatsService;
import com.marry.ratelimit.strategy.RateLimitStrategy;
import com.marry.ratelimit.strategy.RateLimitStrategyFactory;
import com.marry.ratelimit.util.AntPathMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.PathMatcher;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * 基于Redis的限流服务实现
 */
@Service
public class RedisRateLimitService implements RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitService.class);

    PathMatcher pathMatcher = new org.springframework.util.AntPathMatcher();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RateLimitConfigService configService;

    @Autowired
    private RateLimitStatsService statsService;

    @Autowired
    private RateLimitStrategyFactory strategyFactory;

    /**
     * 令牌桶算法的Lua脚本
     * 实现原子性的令牌获取操作
     */
    private static final String TOKEN_BUCKET_SCRIPT =
        "local key = KEYS[1]\n" +
        "local capacity = tonumber(ARGV[1])\n" +
        "local tokens = tonumber(ARGV[2])\n" +
        "local interval = tonumber(ARGV[3])\n" +
        "local requested = tonumber(ARGV[4])\n" +
        "\n" +
        "local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')\n" +
        "local current_tokens = tonumber(bucket[1])\n" +
        "local last_refill = tonumber(bucket[2])\n" +
        "local now = redis.call('TIME')[1]\n" +
        "\n" +
        "if current_tokens == nil then\n" +
        "    current_tokens = capacity\n" +
        "    last_refill = now\n" +
        "end\n" +
        "\n" +
        "-- 计算需要补充的令牌数\n" +
        "local elapsed = math.max(0, now - last_refill)\n" +
        "local tokens_to_add = math.floor(elapsed / interval * tokens)\n" +
        "current_tokens = math.min(capacity, current_tokens + tokens_to_add)\n" +
        "\n" +
        "local allowed = 0\n" +
        "if current_tokens >= requested then\n" +
        "    current_tokens = current_tokens - requested\n" +
        "    allowed = 1\n" +
        "end\n" +
        "\n" +
        "-- 更新令牌桶状态\n" +
        "redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill', now)\n" +
        "redis.call('EXPIRE', key, interval * 2)\n" +
        "\n" +
        "return {allowed, current_tokens}";

    private final DefaultRedisScript<List> tokenBucketScript;

    public RedisRateLimitService() {
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
        try {
            // 1. 首先检查基础路径限流（必须通过）
            boolean pathAllowed = isAllowed(request, rule);
            if (!pathAllowed) {
                return false;
            }

            // 2. 如果启用了IP限流，检查IP维度
            if (rule.isEnableIpLimit()) {
                boolean ipAllowed = checkIpRateLimit(request, rule);
                if (!ipAllowed) {
                    return false;
                }
            }

            // 3. 如果启用了用户限流，检查用户维度
            if (rule.isEnableUserLimit()) {
                boolean userAllowed = checkUserRateLimit(request, rule);
                if (!userAllowed) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("多维度限流检查异常: " + rule.getName(), e);
            return true; // 异常情况下允许通过
        }
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
            return checkIpRateLimit(request, rule);
        }

        String userKey = userStrategy.generateKey(request, rule);
        int capacity = rule.getUserBucketCapacity() != null ? rule.getUserBucketCapacity() : rule.getBucketCapacity();
        int rate = rule.getUserRequestLimit() != null ? rule.getUserRequestLimit() : rule.getRefillRate();

        return checkTokenBucket(userKey, capacity, rate, rule.getTimeWindow());
    }

    /**
     * 执行令牌桶算法检查
     */
    private boolean checkTokenBucket(String key, int capacity, int rate, int timeWindow) {
        try {
            List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                Arrays.asList(key),
                capacity,
                rate,
                timeWindow,
                1 // 请求1个令牌
            );

            if (result != null && result.size() >= 2) {
                long allowed = result.get(0);
                return allowed == 1;
            }

            return false;
        } catch (Exception e) {
            logger.error("令牌桶检查异常: key=" + key, e);
            return true; // 异常情况下允许通过
        }
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

    /**
     * 检查请求是否匹配规则
     */
    private boolean matchesRule(HttpServletRequest request, RateLimitRule rule) {
        // 检查路径模式
        String requestPath = request.getRequestURI();
        if (!pathMatcher.match(rule.getPathPattern(), requestPath)) {
            return false;
        }

        // 检查HTTP方法
        if (rule.getHttpMethods() != null && !rule.getHttpMethods().isEmpty()) {
            HttpMethod requestMethod = HttpMethod.fromString(request.getMethod());
            if (requestMethod == null || !rule.getHttpMethods().contains(requestMethod)) {
                return false;
            }
        }

        return true;
    }



    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先从X-Forwarded-For头获取真实IP
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            return xForwardedFor.split(",")[0].trim();
        }

        // 从X-Real-IP头获取
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        // 从Proxy-Client-IP头获取
        String proxyClientIp = request.getHeader("Proxy-Client-IP");
        if (proxyClientIp != null && !proxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(proxyClientIp)) {
            return proxyClientIp;
        }

        // 从WL-Proxy-Client-IP头获取
        String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
        if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(wlProxyClientIp)) {
            return wlProxyClientIp;
        }

        // 最后从RemoteAddr获取
        return request.getRemoteAddr();
    }

    /**
     * 获取用户ID
     */
    private String getUserId(HttpServletRequest request) {
        // 优先从请求参数获取用户ID
        String userId = request.getParameter("userId");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }

        // 从请求头获取用户ID
        userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }

        // 从Session获取用户ID（如果有的话）
        if (request.getSession(false) != null) {
            Object sessionUserId = request.getSession(false).getAttribute("userId");
            if (sessionUserId != null) {
                return sessionUserId.toString();
            }
        }

        // 如果都没有，返回null
        return null;
    }
}
