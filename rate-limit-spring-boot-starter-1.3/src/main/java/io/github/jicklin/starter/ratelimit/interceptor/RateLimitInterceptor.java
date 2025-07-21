package io.github.jicklin.starter.ratelimit.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jicklin.starter.ratelimit.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 限流拦截器
 */
public class RateLimitInterceptor extends HandlerInterceptorAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            // 检查是否被限流
            boolean allowed = rateLimitService.isAllowed(request);

            if (!allowed) {
                handleRateLimitExceeded(request, response);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("限流拦截器异常", e);
            // 异常情况下允许请求通过
            return true;
        }
    }

    /**
     * 处理限流超出的情况
     */
    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response) throws IOException {
//        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> result = new HashMap<>();
        result.put("error", HttpStatus.TOO_MANY_REQUESTS.value());
        result.put("message", "请求过于频繁，请稍后再试");
        result.put("timestamp", System.currentTimeMillis());
        result.put("path", request.getRequestURI());

        String jsonResponse = objectMapper.writeValueAsString(result);
        response.getWriter().write(jsonResponse);

        logger.debug("请求被限流: {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                getClientIpAddress(request));
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
