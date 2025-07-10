package com.marry.ratelimit.controller;

import com.marry.ratelimit.starter.model.HttpMethod;
import com.marry.ratelimit.starter.model.RateLimitRule;
import com.marry.ratelimit.starter.model.RateLimitStats;
import com.marry.ratelimit.starter.service.RateLimitConfigService;
import com.marry.ratelimit.starter.service.RateLimitService;
import com.marry.ratelimit.starter.service.RateLimitStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用限流starter的示例控制器
 */
@RestController
@RequestMapping("/starter-example")
public class StarterExampleController {
    
    @Autowired
    private RateLimitConfigService configService;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Autowired
    private RateLimitStatsService statsService;
    
    /**
     * 创建限流规则示例
     */
    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> createRule() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 创建API接口限流规则
            RateLimitRule apiRule = new RateLimitRule();
            apiRule.setName("Starter API限流");
            apiRule.setDescription("使用starter创建的API接口限流规则");
            apiRule.setPathPattern("/starter-example/api/**");
            apiRule.setHttpMethods(Arrays.asList(HttpMethod.GET, HttpMethod.POST));
            apiRule.setBucketCapacity(20);
            apiRule.setRefillRate(10);
            apiRule.setTimeWindow(1);
            apiRule.setEnabled(true);
            apiRule.setPriority(100);
            apiRule.setEnableIpLimit(true);
            apiRule.setIpRequestLimit(5);
            apiRule.setIpBucketCapacity(10);
            
            RateLimitRule savedRule = configService.saveRule(apiRule);
            
            result.put("success", true);
            result.put("message", "限流规则创建成功");
            result.put("rule", savedRule);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 获取所有规则
     */
    @GetMapping("/rules")
    public ResponseEntity<List<RateLimitRule>> getAllRules() {
        List<RateLimitRule> rules = configService.getAllRules();
        return ResponseEntity.ok(rules);
    }
    
    /**
     * 测试API接口（会被限流）
     */
    @GetMapping("/api/test")
    public ResponseEntity<Map<String, Object>> testApi(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        // 手动检查限流（可选，因为拦截器会自动处理）
        boolean allowed = rateLimitService.isAllowed(request);
        
        result.put("allowed", allowed);
        result.put("message", allowed ? "请求成功" : "请求被限流");
        result.put("timestamp", System.currentTimeMillis());
        result.put("path", request.getRequestURI());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 高频测试接口
     */
    @PostMapping("/api/high-frequency")
    public ResponseEntity<Map<String, Object>> highFrequencyApi(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("message", "高频接口调用成功");
        result.put("timestamp", System.currentTimeMillis());
        result.put("method", request.getMethod());
        result.put("path", request.getRequestURI());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<List<RateLimitStats>> getStats() {
        List<RateLimitStats> stats = statsService.getAllStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取全局统计信息
     */
    @GetMapping("/stats/global")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> globalStats = statsService.getGlobalStats();
        return ResponseEntity.ok(globalStats);
    }
    
    /**
     * 重置所有限流状态
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetAll() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            rateLimitService.resetAll();
            result.put("success", true);
            result.put("message", "重置成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 切换规则状态
     */
    @PutMapping("/rules/{ruleId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable String ruleId, 
                                                          @RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            configService.toggleRule(ruleId, enabled);
            result.put("success", true);
            result.put("message", enabled ? "规则已启用" : "规则已禁用");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 删除规则
     */
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable String ruleId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            configService.deleteRule(ruleId);
            result.put("success", true);
            result.put("message", "规则删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 获取剩余令牌数
     */
    @GetMapping("/tokens/{ruleId}")
    public ResponseEntity<Map<String, Object>> getRemainingTokens(@PathVariable String ruleId,
                                                                  HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            RateLimitRule rule = configService.getRule(ruleId);
            if (rule == null) {
                result.put("success", false);
                result.put("message", "规则不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            long remainingTokens = rateLimitService.getRemainingTokens(request, rule);
            
            result.put("success", true);
            result.put("ruleId", ruleId);
            result.put("ruleName", rule.getName());
            result.put("remainingTokens", remainingTokens);
            result.put("bucketCapacity", rule.getBucketCapacity());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}
