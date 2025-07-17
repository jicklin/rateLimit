package io.github.jicklin.starter.ratelimit.processor.builtin;

import io.github.jicklin.starter.ratelimit.processor.ParamValueProcessor;

/**
 * 掩码值处理器
 * 对敏感信息进行掩码处理
 *
 * @author marry
 */
public class MaskValueProcessor implements ParamValueProcessor {

    public static final String NAME = "mask";

    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }

        String stringValue = originalValue.toString();

        // 根据参数名判断掩码策略
        if (isSensitiveParam(paramName)) {
            return maskSensitiveValue(stringValue);
        }

        return stringValue;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 判断是否为敏感参数
     */
    private boolean isSensitiveParam(String paramName) {
        if (paramName == null) {
            return false;
        }

        String lowerName = paramName.toLowerCase();
        return lowerName.contains("password") ||
               lowerName.contains("pwd") ||
               lowerName.contains("secret") ||
               lowerName.contains("token") ||
               lowerName.contains("key") ||
               lowerName.contains("phone") ||
               lowerName.contains("mobile") ||
               lowerName.contains("email") ||
               lowerName.contains("idcard") ||
               lowerName.contains("bankcard");
    }

    /**
     * 掩码敏感值
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 2) {
            return "***";
        }

        if (value.length() <= 6) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }

        // 保留前2位和后2位，中间用*替换
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
