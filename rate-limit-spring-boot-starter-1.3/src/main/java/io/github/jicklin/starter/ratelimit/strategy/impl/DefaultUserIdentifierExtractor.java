package io.github.jicklin.starter.ratelimit.strategy.impl;

import io.github.jicklin.starter.ratelimit.strategy.UserIdentifierExtractor;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 默认用户标识提取器
 * 按优先级尝试以下方式获取用户标识：
 * 1. Authorization header中的token
 * 2. 请求参数中的userId
 * 3. Session中的用户信息
 * 4. 客户端IP地址
 *
 * @author marry
 */
@Component
public class DefaultUserIdentifierExtractor implements UserIdentifierExtractor {

    @Override
    public String extractUserIdentifier(HttpServletRequest request) {
        // 1. 尝试从Authorization header获取token
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            // 移除Bearer前缀
            if (authorization.startsWith("Bearer ")) {
                return "token:" + authorization.substring(7);
            }
            return "auth:" + authorization;
        }

        // 2. 尝试从请求参数获取userId
        String userId = request.getParameter("netUserId");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }

        // 3. 尝试从Session获取用户信息
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userIdObj = session.getAttribute("netUserId");
            if (userIdObj != null) {
                return "session:" + userIdObj.toString();
            }

            Object usernameObj = session.getAttribute("username");
            if (usernameObj != null) {
                return "session:" + usernameObj.toString();
            }

            // 使用sessionId作为用户标识
            return "session:" + session.getId();
        }

        // 4. 使用客户端IP作为最后的备选方案
        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // 最低优先级，作为默认实现
    }

    /**
     * 获取客户端真实IP地址
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

        String xForwardedProto = request.getHeader("X-Forwarded-Proto");
        if (xForwardedProto != null && !xForwardedProto.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedProto)) {
            return xForwardedProto;
        }

        return request.getRemoteAddr();
    }
}
