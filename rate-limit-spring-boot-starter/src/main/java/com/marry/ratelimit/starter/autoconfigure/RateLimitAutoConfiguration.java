package com.marry.ratelimit.starter.autoconfigure;

import com.marry.ratelimit.starter.annotation.EnableRateLimit;
import com.marry.ratelimit.starter.interceptor.RateLimitInterceptor;
import com.marry.ratelimit.starter.service.RateLimitConfigService;
import com.marry.ratelimit.starter.service.RateLimitService;
import com.marry.ratelimit.starter.service.RateLimitStatsService;
import com.marry.ratelimit.starter.service.impl.RedisRateLimitConfigService;
import com.marry.ratelimit.starter.service.impl.RedisRateLimitService;
import com.marry.ratelimit.starter.service.impl.RedisRateLimitStatsService;
import com.marry.ratelimit.starter.strategy.RateLimitStrategy;
import com.marry.ratelimit.starter.strategy.RateLimitStrategyFactory;
import com.marry.ratelimit.starter.strategy.impl.IpRateLimitStrategy;
import com.marry.ratelimit.starter.strategy.impl.PathRateLimitStrategy;
import com.marry.ratelimit.starter.strategy.impl.UserRateLimitStrategy;
import com.marry.ratelimit.starter.util.RedisKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * 限流自动配置类
 *
 * 通过@EnableRateLimit注解触发，而不是通过spring.factories自动配置
 */
@Configuration
@ConditionalOnClass({RedisTemplate.class})
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    /**
     * 配置RedisTemplate
     */
    @Bean
    @ConditionalOnMissingBean(name = "rateLimitRedisTemplate")
    public RedisTemplate<String, Object> rateLimitRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 设置键的序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 设置值的序列化器
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 路径限流策略
     */
    @Bean
    @ConditionalOnMissingBean
    public PathRateLimitStrategy pathRateLimitStrategy() {
        return new PathRateLimitStrategy();
    }

    /**
     * IP限流策略
     */
    @Bean
    @ConditionalOnMissingBean
    public IpRateLimitStrategy ipRateLimitStrategy() {
        return new IpRateLimitStrategy();
    }

    /**
     * 用户限流策略
     */
    @Bean
    @ConditionalOnMissingBean
    public UserRateLimitStrategy userRateLimitStrategy() {
        return new UserRateLimitStrategy();
    }

    /**
     * 限流策略工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitStrategyFactory rateLimitStrategyFactory(List<RateLimitStrategy> strategies) {
        return new RateLimitStrategyFactory(strategies);
    }

    /**
     * 限流配置服务
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitConfigService rateLimitConfigService(RedisTemplate<String, Object> redisTemplate, RedisKeyGenerator redisKeyGenerator) {

        return new RedisRateLimitConfigService(redisTemplate, redisKeyGenerator);
    }

    /**
     * 限流统计服务
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitStatsService rateLimitStatsService(RedisTemplate<String, Object> redisTemplate,
                                                       RateLimitConfigService configService,
                                                       IpRateLimitStrategy ipStrategy,
                                                       UserRateLimitStrategy userStrategy,
                                                       RedisKeyGenerator redisKeyGenerator,
                                                       RateLimitProperties properties) {
        return new RedisRateLimitStatsService(redisTemplate, configService, ipStrategy, userStrategy, redisKeyGenerator,properties);
    }

    /**
     * 限流服务
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitService rateLimitService(RedisTemplate<String, Object> redisTemplate,
                                           RateLimitConfigService configService,
                                           RateLimitStatsService statsService,
                                           RateLimitStrategyFactory strategyFactory) {
        return new RedisRateLimitService(redisTemplate, configService, statsService, strategyFactory);
    }

    /**
     * 限流拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitInterceptor rateLimitInterceptor(RateLimitService rateLimitService) {
        return new RateLimitInterceptor(rateLimitService);
    }

    /**
     * Web MVC配置器
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitWebMvcConfigurer rateLimitWebMvcConfigurer(RateLimitProperties properties,
                                                               RateLimitInterceptor rateLimitInterceptor) {
        return new RateLimitWebMvcConfigurer(properties, rateLimitInterceptor);
    }


    @Bean
    @ConditionalOnMissingBean
    public RedisKeyGenerator redisKeyGenerator(RateLimitProperties properties) {
        return new RedisKeyGenerator(properties.getRedisKeyPrefix());
    }


}
