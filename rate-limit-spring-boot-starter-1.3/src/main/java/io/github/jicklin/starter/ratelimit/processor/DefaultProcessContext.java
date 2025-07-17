package io.github.jicklin.starter.ratelimit.processor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 默认处理上下文实现
 *
 * @author marry
 */
public class DefaultProcessContext implements ParamValueProcessor.ProcessContext {

    private final ProceedingJoinPoint joinPoint;
    private final HttpServletRequest request;
    private final int parameterIndex;
    private final String userIdentifier;
    private final Map<String, Object> attributes;

    public DefaultProcessContext(ProceedingJoinPoint joinPoint, HttpServletRequest request,
                               int parameterIndex, String userIdentifier) {
        this.joinPoint = joinPoint;
        this.request = request;
        this.parameterIndex = parameterIndex;
        this.userIdentifier = userIdentifier;
        this.attributes = new HashMap<>();
    }

    @Override
    public String getMethodName() {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getName();
    }

    @Override
    public String getClassName() {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringTypeName();
    }

    @Override
    public int getParameterIndex() {
        return parameterIndex;
    }

    @Override
    public Class<?> getParameterType() {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] parameterTypes = signature.getParameterTypes();
        if (parameterIndex >= 0 && parameterIndex < parameterTypes.length) {
            return parameterTypes[parameterIndex];
        }
        return Object.class;
    }

    @Override
    public Object[] getAllParameters() {
        return joinPoint.getArgs();
    }

    @Override
    public String getUserIdentifier() {
        return userIdentifier;
    }

    @Override
    public String getRequestPath() {
        return request != null ? request.getRequestURI() : null;
    }

    @Override
    public String getHttpMethod() {
        return request != null ? request.getMethod() : null;
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取HTTP请求对象
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 获取AOP连接点
     */
    public ProceedingJoinPoint getJoinPoint() {
        return joinPoint;
    }
}
