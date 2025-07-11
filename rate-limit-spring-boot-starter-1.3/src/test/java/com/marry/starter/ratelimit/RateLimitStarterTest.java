package com.marry.starter.ratelimit;

import com.marry.ratelimit.starter.model.HttpMethod;
import com.marry.ratelimit.starter.model.RateLimitRule;
import com.marry.ratelimit.starter.util.AntPathMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流Starter测试类
 */
public class RateLimitStarterTest {

    @Test
    public void testAntPathMatcher() {
        // 测试Ant路径匹配
        assertTrue(AntPathMatcher.match("/api/**", "/api/users"));
        assertTrue(AntPathMatcher.match("/api/**", "/api/users/123"));
        assertTrue(AntPathMatcher.match("/api/*", "/api/users"));
        assertFalse(AntPathMatcher.match("/api/*", "/api/users/123"));
        assertTrue(AntPathMatcher.match("/user/*/profile", "/user/123/profile"));
        assertFalse(AntPathMatcher.match("/user/*/profile", "/user/123/settings"));
    }

    @Test
    public void testRateLimitRule() {
        // 测试限流规则模型
        RateLimitRule rule = new RateLimitRule();
        rule.setName("测试规则");
        rule.setPathPattern("/api/**");
        rule.setHttpMethods(Arrays.asList(HttpMethod.GET, HttpMethod.POST));
        rule.setBucketCapacity(10);
        rule.setRefillRate(5);
        rule.setTimeWindow(1);
        rule.setEnabled(true);
        rule.setEnableIpLimit(true);
        rule.setIpRequestLimit(3);

        assertEquals("测试规则", rule.getName());
        assertEquals("/api/**", rule.getPathPattern());
        assertEquals(10, rule.getBucketCapacity());
        assertEquals(5, rule.getRefillRate());
        assertTrue(rule.isEnabled());
        assertTrue(rule.isEnableIpLimit());
        assertEquals(3, rule.getIpRequestLimit().intValue());
    }

    @Test
    public void testHttpMethodEnum() {
        // 测试HTTP方法枚举
        assertEquals(HttpMethod.GET, HttpMethod.fromString("GET"));
        assertEquals(HttpMethod.POST, HttpMethod.fromString("post"));
        assertEquals(HttpMethod.PUT, HttpMethod.fromString("Put"));
        assertNull(HttpMethod.fromString("INVALID"));
        assertNull(HttpMethod.fromString(null));
    }

    @Test
    public void testMockHttpRequest() {
        // 测试Mock HTTP请求
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/users");
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "10.0.0.1");

        assertEquals("GET", request.getMethod());
        assertEquals("/api/users", request.getRequestURI());
        assertEquals("192.168.1.100", request.getRemoteAddr());
        assertEquals("10.0.0.1", request.getHeader("X-Forwarded-For"));
    }
}
