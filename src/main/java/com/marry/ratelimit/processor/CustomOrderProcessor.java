package com.marry.ratelimit.processor;

import com.marry.starter.ratelimit.processor.ParamValueProcessor;
import org.springframework.stereotype.Component;

/**
 * 自定义订单处理器示例
 * 演示如何在引用项目中自定义参数值处理器
 * 
 * @author marry
 */
@Component
public class CustomOrderProcessor implements ParamValueProcessor {

    public static final String NAME = "custom_order";

    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }
        
        String stringValue = originalValue.toString();
        
        // 自定义处理逻辑：订单号标准化
        if (paramName.toLowerCase().contains("order")) {
            return processOrderNumber(stringValue, context);
        }
        
        // 自定义处理逻辑：用户ID处理
        if (paramName.toLowerCase().contains("user")) {
            return processUserId(stringValue, context);
        }
        
        return stringValue;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 处理订单号
     */
    private String processOrderNumber(String orderNumber, ProcessContext context) {
        // 移除前缀
        if (orderNumber.startsWith("ORD")) {
            orderNumber = orderNumber.substring(3);
        }
        
        // 统一长度（补零）
        while (orderNumber.length() < 10) {
            orderNumber = "0" + orderNumber;
        }
        
        // 添加用户标识前缀
        String userIdentifier = context.getUserIdentifier();
        if (userIdentifier != null && !userIdentifier.isEmpty()) {
            return userIdentifier.substring(0, Math.min(3, userIdentifier.length())) + "_" + orderNumber;
        }
        
        return orderNumber;
    }

    /**
     * 处理用户ID
     */
    private String processUserId(String userId, ProcessContext context) {
        // 移除特殊字符
        userId = userId.replaceAll("[^a-zA-Z0-9]", "");
        
        // 转换为大写
        userId = userId.toUpperCase();
        
        // 限制长度
        if (userId.length() > 20) {
            userId = userId.substring(0, 20);
        }
        
        return userId;
    }
}
