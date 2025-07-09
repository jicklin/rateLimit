package com.marry.ratelimit.config;

import com.marry.ratelimit.model.HttpMethod;
import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.service.RateLimitConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 数据初始化器 - 创建默认的限流规则用于测试
 */
@Component
public class DataInitializer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    
    @Autowired
    private RateLimitConfigService configService;
    
    @Override
    public void run(String... args) throws Exception {
        try {
            // 检查是否已有规则，如果没有则创建默认规则
            if (configService.getAllRules().isEmpty()) {
                createDefaultRules();
                logger.info("已创建默认限流规则");
            } else {
                logger.info("限流规则已存在，跳过初始化");
            }
        } catch (Exception e) {
            logger.error("初始化默认数据失败", e);
        }
    }
    
    private void createDefaultRules() {
        // 1. API接口限流规则
        RateLimitRule apiRule = new RateLimitRule();
        apiRule.setName("API接口限流");
        apiRule.setDescription("对所有API接口进行限流，防止接口被恶意调用");
        apiRule.setPathPattern("/api/**");
        apiRule.setHttpMethods(Arrays.asList(HttpMethod.GET, HttpMethod.POST));
        apiRule.setBucketCapacity(20);
        apiRule.setRefillRate(10);
        apiRule.setTimeWindow(1);
        apiRule.setEnabled(true);
        apiRule.setPriority(100);
        apiRule.setEnableIpLimit(true);
        apiRule.setIpRequestLimit(5);
        apiRule.setIpBucketCapacity(10);
        configService.saveRule(apiRule);
        
        // 2. 测试接口限流规则
        RateLimitRule testRule = new RateLimitRule();
        testRule.setName("测试接口限流");
        testRule.setDescription("对测试接口进行严格限流");
        testRule.setPathPattern("/test/**");
        testRule.setBucketCapacity(10);
        testRule.setRefillRate(5);
        testRule.setTimeWindow(1);
        testRule.setEnabled(true);
        testRule.setPriority(200);
        testRule.setEnableIpLimit(true);
        testRule.setIpRequestLimit(3);
        testRule.setIpBucketCapacity(5);
        testRule.setEnableUserLimit(true);
        testRule.setUserRequestLimit(2);
        testRule.setUserBucketCapacity(3);
        configService.saveRule(testRule);
        
        // 3. 高频接口限流规则
        RateLimitRule highFreqRule = new RateLimitRule();
        highFreqRule.setName("高频接口限流");
        highFreqRule.setDescription("对高频访问接口进行限流");
        highFreqRule.setPathPattern("/test/high-frequency");
        highFreqRule.setBucketCapacity(5);
        highFreqRule.setRefillRate(2);
        highFreqRule.setTimeWindow(1);
        highFreqRule.setEnabled(true);
        highFreqRule.setPriority(50);
        highFreqRule.setEnableIpLimit(true);
        highFreqRule.setIpRequestLimit(1);
        highFreqRule.setIpBucketCapacity(2);
        configService.saveRule(highFreqRule);
        
        // 4. 用户操作限流规则
        RateLimitRule userRule = new RateLimitRule();
        userRule.setName("用户操作限流");
        userRule.setDescription("对用户相关操作进行限流");
        userRule.setPathPattern("/test/user/*");
        userRule.setHttpMethods(Arrays.asList(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT));
        userRule.setBucketCapacity(15);
        userRule.setRefillRate(8);
        userRule.setTimeWindow(1);
        userRule.setEnabled(true);
        userRule.setPriority(150);
        userRule.setEnableUserLimit(true);
        userRule.setUserRequestLimit(5);
        userRule.setUserBucketCapacity(8);
        configService.saveRule(userRule);
    }
}
