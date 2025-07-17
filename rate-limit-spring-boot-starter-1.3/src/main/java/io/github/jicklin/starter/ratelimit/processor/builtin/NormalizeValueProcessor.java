package io.github.jicklin.starter.ratelimit.processor.builtin;

import io.github.jicklin.starter.ratelimit.processor.ParamValueProcessor;

/**
 * 标准化值处理器
 * 对参数值进行标准化处理（去空格、转小写等）
 *
 * @author marry
 */
public class NormalizeValueProcessor implements ParamValueProcessor {

    public static final String NAME = "normalize";

    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }

        String stringValue = originalValue.toString();

        // 去除前后空格
        stringValue = stringValue.trim();

        // 转换为小写（适用于不区分大小写的场景）
        if (shouldLowerCase(paramName)) {
            stringValue = stringValue.toLowerCase();
        }

        // 移除多余的空格
        stringValue = stringValue.replaceAll("\\s+", " ");

        return stringValue;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 判断是否应该转换为小写
     */
    private boolean shouldLowerCase(String paramName) {
        if (paramName == null) {
            return false;
        }

        String lowerName = paramName.toLowerCase();
        return lowerName.contains("email") ||
               lowerName.contains("username") ||
               lowerName.contains("account") ||
               lowerName.contains("code") ||
               lowerName.contains("type");
    }
}
