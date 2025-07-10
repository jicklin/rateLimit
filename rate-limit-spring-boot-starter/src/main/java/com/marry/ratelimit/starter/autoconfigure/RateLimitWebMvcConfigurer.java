package com.marry.ratelimit.starter.autoconfigure;

import com.marry.ratelimit.starter.interceptor.RateLimitInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 限流Web MVC配置器
 */
public class RateLimitWebMvcConfigurer implements WebMvcConfigurer {

    private final RateLimitProperties properties;
    private final RateLimitInterceptor rateLimitInterceptor;

    public RateLimitWebMvcConfigurer(RateLimitProperties properties, RateLimitInterceptor rateLimitInterceptor) {
        this.properties = properties;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.getInterceptor().isEnabled()) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns(properties.getInterceptor().getPathPatterns())
                    .excludePathPatterns(properties.getInterceptor().getExcludePathPatterns())
                    .order(properties.getInterceptor().getOrder());
        }
    }
}
