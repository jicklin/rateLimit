package io.github.jicklin.starter.ratelimit.processor.builtin;

import io.github.jicklin.starter.ratelimit.processor.ParamValueProcessor;

/**
 * 默认值处理器
 * 直接返回原始值
 *
 * @author marry
 */
public class DefaultValueProcessor implements ParamValueProcessor {

    public static final String NAME = "default";

    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        return originalValue;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
