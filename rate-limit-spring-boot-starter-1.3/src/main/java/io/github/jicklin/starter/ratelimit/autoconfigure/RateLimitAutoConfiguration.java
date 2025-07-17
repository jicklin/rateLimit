package io.github.jicklin.starter.ratelimit.autoconfigure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.jicklin.starter.ratelimit.interceptor.RateLimitInterceptor;
import io.github.jicklin.starter.ratelimit.service.RateLimitConfigService;
import io.github.jicklin.starter.ratelimit.service.RateLimitService;
import io.github.jicklin.starter.ratelimit.service.RateLimitStatsService;
import io.github.jicklin.starter.ratelimit.service.impl.OptimizedRateLimitStatsService;
import io.github.jicklin.starter.ratelimit.service.impl.RedisRateLimitConfigService;
import io.github.jicklin.starter.ratelimit.service.impl.RedisRateLimitService;
import io.github.jicklin.starter.ratelimit.service.impl.RedisRateLimitStatsService;
import io.github.jicklin.starter.ratelimit.strategy.RateLimitStrategy;
import io.github.jicklin.starter.ratelimit.strategy.RateLimitStrategyFactory;
import io.github.jicklin.starter.ratelimit.strategy.impl.IpRateLimitStrategy;
import io.github.jicklin.starter.ratelimit.strategy.impl.PathRateLimitStrategy;
import io.github.jicklin.starter.ratelimit.strategy.impl.UserRateLimitStrategy;
import io.github.jicklin.starter.ratelimit.util.SpringBootVersionChecker;
import io.github.jicklin.starter.ratelimit.util.RedisKeyGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

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
@Import({RateLimitStatsConfiguration.class, DuplicateSubmitAutoConfiguration.class})
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
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

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
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, false);
        om.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        return om;
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

   /* *//**
     * 限流拦截器
     */
    @Bean
    public RateLimitInterceptor rateLimitInterceptor(RateLimitService rateLimitService) {
        return new RateLimitInterceptor(rateLimitService);
    }

   /* *//**//**
     * Web MVC配置器
     *//*
    @Bean
    @ConditionalOnMissingBean
    public RateLimitWebMvcConfigurer rateLimitWebMvcConfigurer(RateLimitProperties properties,RateLimitInterceptor rateLimitInterceptor) {
        return new RateLimitWebMvcConfigurer(properties,rateLimitInterceptor);
    }*/


    @Bean
    @ConditionalOnMissingBean
    public RedisKeyGenerator redisKeyGenerator(RateLimitProperties properties) {
        return new RedisKeyGenerator(properties.getRedisKeyPrefix());
    }


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
            RedisKeyGenerator keyGenerator,
            RateLimitProperties properties) {
        return new OptimizedRateLimitStatsService(redisTemplate, configService, ipStrategy, userStrategy, keyGenerator, properties);
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

    @Configuration
    public static class WebConfig extends WebMvcConfigurerAdapter {

        @Autowired
        private RateLimitInterceptor rateLimitInterceptor;

        @Autowired
        private  RateLimitProperties properties;


        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            if (properties.getInterceptor().isEnabled()) {
                registry.addInterceptor(rateLimitInterceptor)
                        .addPathPatterns(properties.getInterceptor().getPathPatterns().toArray(new String[0]))
                        .excludePathPatterns(properties.getInterceptor().getExcludePathPatterns().toArray(new String[0]));
            }
        }
    }

    // ==================== 防重复提交相关配置 ====================



}
