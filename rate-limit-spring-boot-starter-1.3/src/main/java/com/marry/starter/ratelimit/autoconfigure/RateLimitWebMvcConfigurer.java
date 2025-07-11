package com.marry.starter.ratelimit.autoconfigure;

import com.marry.starter.ratelimit.interceptor.RateLimitInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * 限流Web MVC配置器
 */
public class RateLimitWebMvcConfigurer extends WebMvcConfigurerAdapter {

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
                    .addPathPatterns(properties.getInterceptor().getPathPatterns().toArray(new String[0]))
                    .excludePathPatterns(properties.getInterceptor().getExcludePathPatterns().toArray(new String[0]));
        }
    }
}
