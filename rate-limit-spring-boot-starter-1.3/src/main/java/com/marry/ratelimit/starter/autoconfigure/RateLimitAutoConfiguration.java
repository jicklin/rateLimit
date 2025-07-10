package com.marry.ratelimit.starter.autoconfigure;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import com.marry.ratelimit.starter.util.SpringBootVersionChecker;
import com.marry.ratelimit.starter.util.SpringBootVersionChecker.CompatibilityLevel;
import com.marry.ratelimit.starter.util.RedisKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
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

    private static final Logger logger = LoggerFactory.getLogger(RateLimitAutoConfiguration.class);

    @PostConstruct
    public void checkVersion() {
        String version = SpringBootVersionChecker.getCurrentVersion();
        logger.info("Rate Limit Starter 初始化，当前SpringBoot版本: {}", version);

        if (!SpringBootVersionChecker.isCompatible()) {
            logger.warn("当前SpringBoot版本 {} 可能与Rate Limit Starter不完全兼容，建议使用SpringBoot 2.0+版本", version);
        }
    }

    /**
     * 配置RedisTemplate（只有在没有RedisConnectionFactory时才跳过）
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> rateLimitRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 设置键的序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 设置值的序列化器
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper()));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper()));

        template.afterPropertiesSet();
        return template;
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.registerModule(new JavaTimeModule());
        return om;
    }

    /**
     * 路径限流策略
     */
    @Bean("starterPathRateLimitStrategy")
    @ConditionalOnMissingBean(name = {"pathRateLimitStrategy", "starterPathRateLimitStrategy"})
    public PathRateLimitStrategy pathRateLimitStrategy() {
        return new PathRateLimitStrategy();
    }

    /**
     * IP限流策略
     */
    @Bean("starterIpRateLimitStrategy")
    @ConditionalOnMissingBean(name = {"ipRateLimitStrategy", "starterIpRateLimitStrategy"})
    public IpRateLimitStrategy ipRateLimitStrategy() {
        return new IpRateLimitStrategy();
    }

    /**
     * 用户限流策略
     */
    @Bean("starterUserRateLimitStrategy")
    @ConditionalOnMissingBean(name = {"userRateLimitStrategy", "starterUserRateLimitStrategy"})
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
    @ConditionalOnMissingBean(RateLimitConfigService.class)
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
