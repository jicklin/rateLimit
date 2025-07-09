package com.marry.ratelimit.config;

import com.marry.ratelimit.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/ratelimit/**",  // 排除限流管理页面
                        "/static/**",     // 排除静态资源
                        "/css/**",        // 排除CSS文件
                        "/js/**",         // 排除JS文件
                        "/images/**",     // 排除图片文件
                        "/favicon.ico"    // 排除图标文件
                );
    }
}
