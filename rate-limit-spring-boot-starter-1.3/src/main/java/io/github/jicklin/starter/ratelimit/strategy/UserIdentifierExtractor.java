package io.github.jicklin.starter.ratelimit.strategy;

import javax.servlet.http.HttpServletRequest;

/**
 * 用户标识提取器接口
 * 用于从请求中提取用户唯一标识
 *
 * @author marry
 */
public interface UserIdentifierExtractor {

    /**
     * 从请求中提取用户标识
     *
     * @param request HTTP请求
     * @return 用户唯一标识，如果无法获取则返回null
     */
    String extractUserIdentifier(HttpServletRequest request);

    /**
     * 获取提取器的优先级，数值越小优先级越高
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}
