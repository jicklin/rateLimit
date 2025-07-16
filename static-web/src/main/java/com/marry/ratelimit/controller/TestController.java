package com.marry.ratelimit.controller;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器 - 用于测试限流功能
 */
@RestController
@RequestMapping("/test")
public class TestController {
    
    /**
     * 测试GET请求
     */
    @GetMapping("/get")
    public Map<String, Object> testGet(HttpServletRequest request) {
        return createResponse("GET", request);
    }
    
    /**
     * 测试POST请求
     */
    @PostMapping("/post")
    public Map<String, Object> testPost(HttpServletRequest request) {
        return createResponse("POST", request);
    }
    
    /**
     * 测试PUT请求
     */
    @PutMapping("/put")
    public Map<String, Object> testPut(HttpServletRequest request) {
        return createResponse("PUT", request);
    }
    
    /**
     * 测试DELETE请求
     */
    @DeleteMapping("/delete")
    public Map<String, Object> testDelete(HttpServletRequest request) {
        return createResponse("DELETE", request);
    }
    
    /**
     * 测试带参数的请求
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> testUser(@PathVariable String userId, HttpServletRequest request) {
        Map<String, Object> response = createResponse("GET", request);
        response.put("userId", userId);
        return response;
    }
    
    /**
     * 测试API接口
     */
    @GetMapping("/api/data")
    public Map<String, Object> testApi(HttpServletRequest request) {
        return createResponse("API", request);
    }
    
    /**
     * 测试高频请求接口
     */
    @GetMapping("/high-frequency")
    public Map<String, Object> testHighFrequency(HttpServletRequest request) {
        return createResponse("HIGH_FREQUENCY", request);
    }
    
    /**
     * 创建响应对象
     */
    private Map<String, Object> createResponse(String method, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("method", method);
        response.put("path", request.getRequestURI());
        response.put("timestamp", System.currentTimeMillis());
        response.put("ip", getClientIpAddress(request));
        response.put("userAgent", request.getHeader("User-Agent"));
        
        // 添加用户ID（如果有的话）
        String userId = request.getParameter("userId");
        if (userId == null) {
            userId = request.getHeader("X-User-Id");
        }
        if (userId != null) {
            response.put("userId", userId);
        }
        
        return response;
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
