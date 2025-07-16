package com.marry.starter.ratelimit.processor;

/**
 * 参数值处理器接口
 * 用于自定义参数值的提取和转换逻辑
 * 
 * @author marry
 */
public interface ParamValueProcessor {

    /**
     * 处理参数值
     *
     * @param originalValue 原始参数值
     * @param paramName 参数名称
     * @param context 处理上下文
     * @return 处理后的值，可以是单个值或集合
     *         如果返回集合，每个元素将作为独立的组别处理
     */
    Object processValue(Object originalValue, String paramName, ProcessContext context);

    /**
     * 获取处理器名称
     * 
     * @return 处理器名称
     */
    String getName();

    /**
     * 处理上下文
     */
    interface ProcessContext {
        
        /**
         * 获取方法名
         */
        String getMethodName();
        
        /**
         * 获取类名
         */
        String getClassName();
        
        /**
         * 获取参数索引
         */
        int getParameterIndex();
        
        /**
         * 获取参数类型
         */
        Class<?> getParameterType();
        
        /**
         * 获取所有参数值
         */
        Object[] getAllParameters();
        
        /**
         * 获取用户标识
         */
        String getUserIdentifier();
        
        /**
         * 获取请求路径
         */
        String getRequestPath();
        
        /**
         * 获取HTTP方法
         */
        String getHttpMethod();
        
        /**
         * 获取自定义属性
         */
        Object getAttribute(String key);
        
        /**
         * 设置自定义属性
         */
        void setAttribute(String key, Object value);
    }
}
