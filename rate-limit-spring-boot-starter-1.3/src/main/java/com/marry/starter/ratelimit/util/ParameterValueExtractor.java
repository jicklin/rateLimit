package com.marry.starter.ratelimit.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 参数值提取器
 * 支持通过路径表达式从对象中提取值
 * 
 * @author marry
 */
@Component
public class ParameterValueExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ParameterValueExtractor.class);

    /**
     * 根据路径提取参数值
     * 
     * @param obj 对象
     * @param path 路径表达式，如 "user.id", "request.orderId"
     * @return 提取的值
     */
    public Object extractValue(Object obj, String path) {
        if (obj == null) {
            return null;
        }

        if (path == null || path.trim().isEmpty()) {
            return obj;
        }

        try {
            String[] parts = path.split("\\.");
            Object current = obj;

            for (String part : parts) {
                if (current == null) {
                    return null;
                }
                current = getFieldValue(current, part);
            }

            return current;
        } catch (Exception e) {
            logger.warn("提取参数值失败: path={}, object={}", path, obj.getClass().getSimpleName(), e);
            return obj; // 提取失败时返回原对象
        }
    }

    /**
     * 获取对象字段值
     */
    private Object getFieldValue(Object obj, String fieldName) throws Exception {
        Class<?> clazz = obj.getClass();

        // 首先尝试通过getter方法获取
        try {
            String getterName = "get" + capitalize(fieldName);
            Method getter = clazz.getMethod(getterName);
            return getter.invoke(obj);
        } catch (NoSuchMethodException e) {
            // 尝试boolean类型的is方法
            try {
                String isGetterName = "is" + capitalize(fieldName);
                Method isGetter = clazz.getMethod(isGetterName);
                return isGetter.invoke(obj);
            } catch (NoSuchMethodException e2) {
                // 最后尝试直接访问字段
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (NoSuchFieldException e3) {
                    // 尝试父类字段
                    return getFieldValueFromSuperClass(obj, fieldName, clazz.getSuperclass());
                }
            }
        }
    }

    /**
     * 从父类中获取字段值
     */
    private Object getFieldValueFromSuperClass(Object obj, String fieldName, Class<?> superClass) throws Exception {
        if (superClass == null || superClass == Object.class) {
            throw new NoSuchFieldException("Field not found: " + fieldName);
        }

        try {
            Field field = superClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            return getFieldValueFromSuperClass(obj, fieldName, superClass.getSuperclass());
        }
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 安全地转换对象为字符串
     */
    public String safeToString(Object obj) {
        if (obj == null) {
            return "null";
        }

        try {
            // 对于基本类型和字符串，直接转换
            if (obj instanceof String || 
                obj instanceof Number || 
                obj instanceof Boolean || 
                obj instanceof Character) {
                return obj.toString();
            }

            // 对于其他对象，尝试调用toString方法
            String result = obj.toString();
            
            // 如果toString返回的是默认格式（类名@哈希码），则返回类名
            if (result.matches(".*@[0-9a-fA-F]+$")) {
                return obj.getClass().getSimpleName();
            }
            
            return result;
        } catch (Exception e) {
            logger.warn("对象转字符串失败: {}", obj.getClass().getSimpleName(), e);
            return obj.getClass().getSimpleName();
        }
    }
}
