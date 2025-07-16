package com.marry.ratelimit.processor;

import com.marry.starter.ratelimit.processor.ParamValueProcessor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义集合处理器示例
 * 演示如何处理复杂对象并返回多个值进行独立控制
 * 
 * @author marry
 */
@Component
public class CustomCollectionProcessor implements ParamValueProcessor {

    public static final String NAME = "custom_collection";

    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }
        
        // 处理不同类型的输入
        if (originalValue instanceof String) {
            return processStringValue((String) originalValue, paramName, context);
        } else if (originalValue instanceof Map) {
            return processMapValue((Map<?, ?>) originalValue, paramName, context);
        } else if (originalValue instanceof List) {
            return processListValue((List<?>) originalValue, paramName, context);
        }
        
        return originalValue.toString();
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 处理字符串值
     */
    private Object processStringValue(String value, String paramName, ProcessContext context) {
        // 如果是商品ID列表格式：PROD001,PROD002,PROD003
        if (paramName.toLowerCase().contains("product") && value.contains(",")) {
            List<String> productIds = new ArrayList<>();
            String[] parts = value.split(",");
            
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    // 标准化商品ID：PROD001 -> PRODUCT_001
                    if (trimmed.startsWith("PROD")) {
                        trimmed = "PRODUCT_" + trimmed.substring(4);
                    }
                    productIds.add(trimmed);
                }
            }
            
            return productIds.isEmpty() ? value : productIds;
        }
        
        // 如果是用户角色列表：admin,user,guest
        if (paramName.toLowerCase().contains("role") && value.contains(",")) {
            List<String> roles = new ArrayList<>();
            String[] parts = value.split(",");
            
            for (String part : parts) {
                String trimmed = part.trim().toUpperCase();
                if (!trimmed.isEmpty()) {
                    // 添加前缀：admin -> ROLE_ADMIN
                    roles.add("ROLE_" + trimmed);
                }
            }
            
            return roles.isEmpty() ? value : roles;
        }
        
        return value;
    }

    /**
     * 处理Map值
     */
    private Object processMapValue(Map<?, ?> map, String paramName, ProcessContext context) {
        List<String> values = new ArrayList<>();
        
        // 提取Map中的关键字段
        if (map.containsKey("categories")) {
            Object categories = map.get("categories");
            if (categories instanceof List) {
                for (Object category : (List<?>) categories) {
                    values.add("CATEGORY_" + category.toString().toUpperCase());
                }
            }
        }
        
        if (map.containsKey("tags")) {
            Object tags = map.get("tags");
            if (tags instanceof List) {
                for (Object tag : (List<?>) tags) {
                    values.add("TAG_" + tag.toString().toLowerCase());
                }
            }
        }
        
        if (map.containsKey("permissions")) {
            Object permissions = map.get("permissions");
            if (permissions instanceof List) {
                for (Object permission : (List<?>) permissions) {
                    values.add("PERM_" + permission.toString().toUpperCase());
                }
            }
        }
        
        return values.isEmpty() ? map.toString() : values;
    }

    /**
     * 处理List值
     */
    private Object processListValue(List<?> list, String paramName, ProcessContext context) {
        List<String> processedValues = new ArrayList<>();
        
        for (Object item : list) {
            if (item != null) {
                String itemStr = item.toString();
                
                // 根据参数名进行不同的处理
                if (paramName.toLowerCase().contains("id")) {
                    // ID类型：添加前缀和用户标识
                    String userPrefix = getUserPrefix(context);
                    processedValues.add(userPrefix + "_ID_" + itemStr);
                } else if (paramName.toLowerCase().contains("code")) {
                    // 代码类型：标准化格式
                    processedValues.add("CODE_" + itemStr.toUpperCase());
                } else {
                    // 其他类型：直接添加
                    processedValues.add(itemStr);
                }
            }
        }
        
        return processedValues.isEmpty() ? list.toString() : processedValues;
    }

    /**
     * 获取用户前缀
     */
    private String getUserPrefix(ProcessContext context) {
        String userIdentifier = context.getUserIdentifier();
        if (userIdentifier != null && userIdentifier.length() >= 3) {
            return userIdentifier.substring(0, 3).toUpperCase();
        }
        return "USR";
    }
}
