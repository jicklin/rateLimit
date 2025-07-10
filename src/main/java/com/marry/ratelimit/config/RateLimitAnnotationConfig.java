package com.marry.ratelimit.config;

import com.marry.ratelimit.starter.annotation.EnableRateLimit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

/**
 * 限流注解配置示例
 *
 * 展示@EnableRateLimit注解的使用方式
 * 具体的功能配置通过application.yml进行设置
 */
public class RateLimitAnnotationConfig {

    /**
     * 示例1: 在主应用类上使用
     */
    @SpringBootApplication
    @EnableRateLimit
    public static class MainApplicationConfig {
        public static void main(String[] args) {
            SpringApplication.run(MainApplicationConfig.class, args);
        }
    }

    /**
     * 示例2: 在配置类上使用
     */
    @Configuration
    @EnableRateLimit
    public static class ConfigurationClassConfig {
        // 在配置类上启用限流功能
        // 具体配置通过application.yml设置
    }

    /**
     * 示例3: 结合配置文件使用
     *
     * application.yml配置示例:
     * rate-limit:
     *   enabled: true
     *   interceptor:
     *     enabled: true
     *     exclude-path-patterns:
     *       - "/static/**"
     *       - "/health"
     *   stats:
     *     enabled: true
     *     retention-hours: 24
     */
    @Configuration
    @EnableRateLimit
    public static class WithConfigFileConfig {
        // 注解启用功能，配置文件控制具体参数
    }
}
