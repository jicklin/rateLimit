package com.marry.ratelimit.starter.model;

/**
 * HTTP方法枚举
 */
public enum HttpMethod {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE");

    private final String method;

    HttpMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public static HttpMethod fromString(String method) {
        if (method == null) {
            return null;
        }
        for (HttpMethod httpMethod : HttpMethod.values()) {
            if (httpMethod.method.equalsIgnoreCase(method)) {
                return httpMethod;
            }
        }
        return null;
    }
}
