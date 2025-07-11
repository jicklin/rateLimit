package com.marry.starter.ratelimit.autoconfigure;

import com.marry.starter.ratelimit.service.RateLimitConfigService;
import com.marry.starter.ratelimit.service.RateLimitStatsService;
import com.marry.starter.ratelimit.service.impl.OptimizedRateLimitStatsService;
import com.marry.starter.ratelimit.service.impl.RedisRateLimitStatsService;
import com.marry.starter.ratelimit.strategy.impl.IpRateLimitStrategy;
import com.marry.starter.ratelimit.strategy.impl.UserRateLimitStrategy;
import com.marry.starter.ratelimit.util.RedisKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 限流统计服务配置
 * 根据配置选择使用标准统计服务还是优化统计服务
 */
@Configuration
public class RateLimitStatsConfiguration {

    /**
     * 优化的统计服务（适用于大量用户场景）
     * 当启用优化模式时使用此服务
     */
    @Bean
    @ConditionalOnProperty(prefix = "rate-limit.stats", name = "optimized", havingValue = "true")
    @ConditionalOnMissingBean(RateLimitStatsService.class)
    public RateLimitStatsService optimizedRateLimitStatsService(
            RedisTemplate<String, Object> redisTemplate,
            RateLimitConfigService configService,
            IpRateLimitStrategy ipStrategy,
            UserRateLimitStrategy userStrategy,
            RedisKeyGenerator keyGenerator) {
        return new OptimizedRateLimitStatsService(redisTemplate, configService, ipStrategy, userStrategy, keyGenerator);
    }

    /**
     * 标准统计服务（默认）
     * 当未启用优化模式时使用此服务
     */
    @Bean
    @ConditionalOnProperty(prefix = "rate-limit.stats", name = "optimized", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(RateLimitStatsService.class)
    public RateLimitStatsService standardRateLimitStatsService(
            RedisTemplate<String, Object> redisTemplate,
            RateLimitConfigService configService,
            IpRateLimitStrategy ipStrategy,
            UserRateLimitStrategy userStrategy,
            RedisKeyGenerator redisKeyGenerator,
            RateLimitProperties properties) {
        return new RedisRateLimitStatsService(redisTemplate, configService, ipStrategy, userStrategy, redisKeyGenerator, properties);
    }
}
