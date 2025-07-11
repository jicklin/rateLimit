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


}
