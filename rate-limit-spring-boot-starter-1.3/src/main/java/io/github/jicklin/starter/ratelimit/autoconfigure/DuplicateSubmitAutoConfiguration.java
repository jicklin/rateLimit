package io.github.jicklin.starter.ratelimit.autoconfigure;

import io.github.jicklin.starter.ratelimit.aspect.DuplicateSubmitAspect;
import io.github.jicklin.starter.ratelimit.config.DuplicateSubmitProperties;
import io.github.jicklin.starter.ratelimit.processor.ParamValueProcessorManager;
import io.github.jicklin.starter.ratelimit.service.DuplicateSubmitService;
import io.github.jicklin.starter.ratelimit.service.impl.RedisDuplicateSubmitService;
import io.github.jicklin.starter.ratelimit.strategy.UserIdentifierExtractor;
import io.github.jicklin.starter.ratelimit.strategy.impl.DefaultUserIdentifierExtractor;
import io.github.jicklin.starter.ratelimit.util.ParameterValueExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 防重复提交自动配置类
 *
 * @author marry
 */
@Configuration
@ConditionalOnProperty(prefix = "rate-limit.duplicate-submit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RedisTemplate.class)
@EnableConfigurationProperties(DuplicateSubmitProperties.class)
public class DuplicateSubmitAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateSubmitAutoConfiguration.class);

    /**
     * 默认用户标识提取器
     */
    @Bean
    @ConditionalOnMissingBean(UserIdentifierExtractor.class)
    public DefaultUserIdentifierExtractor defaultUserIdentifierExtractor() {
        logger.debug("创建默认用户标识提取器");
        return new DefaultUserIdentifierExtractor();
    }

    /**
     * 参数值提取器
     */
    @Bean
    @ConditionalOnMissingBean(ParameterValueExtractor.class)
    public ParameterValueExtractor parameterValueExtractor() {
        logger.debug("创建参数值提取器");
        return new ParameterValueExtractor();
    }

    /**
     * 参数值处理器管理器
     */
    @Bean
    @ConditionalOnMissingBean(ParamValueProcessorManager.class)
    public ParamValueProcessorManager paramValueProcessorManager() {
        logger.debug("创建参数值处理器管理器");
        return new ParamValueProcessorManager();
    }

    /**
     * 防重复提交服务
     */
    @Bean
    public RedisDuplicateSubmitService duplicateSubmitService() {
        logger.debug("创建Redis防重复提交服务");
        return new RedisDuplicateSubmitService();
    }

    /**
     * 防重复提交切面
     */
    @Bean
    @ConditionalOnMissingBean(DuplicateSubmitAspect.class)
    public DuplicateSubmitAspect duplicateSubmitAspect(DuplicateSubmitService duplicateSubmitService) {
        logger.debug("创建防重复提交AOP切面 - DuplicateSubmitAspect");
        DuplicateSubmitAspect aspect = new DuplicateSubmitAspect(duplicateSubmitService);
        logger.debug("防重复提交切面创建成功: {}", aspect.getClass().getName());
        return aspect;
    }

    /**
     * 防重复提交配置信息
     */
    @Bean
    @ConditionalOnMissingBean(DuplicateSubmitConfigInfo.class)
    public DuplicateSubmitConfigInfo duplicateSubmitConfigInfo() {
        DuplicateSubmitConfigInfo configInfo = new DuplicateSubmitConfigInfo();
        logger.debug("防重复提交功能已启用: {}", configInfo);
        return configInfo;
    }

    /**
     * 防重复提交配置信息类
     */
    public static class DuplicateSubmitConfigInfo {
        private final String version = "1.3.1";
        private final String implementation = "Redis SETNX + Lua Script";
        private final String[] features = {
            "原子性操作",
            "参数级别控制",
            "对象属性提取",
            "Key生成优化",
            "安全锁管理",
            "多种参数策略"
        };

        public String getVersion() {
            return version;
        }

        public String getImplementation() {
            return implementation;
        }

        public String[] getFeatures() {
            return features;
        }

        @Override
        public String toString() {
            return String.format("DuplicateSubmitConfig{version='%s', implementation='%s', features=%d}",
                version, implementation, features.length);
        }
    }
}
