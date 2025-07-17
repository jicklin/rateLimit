package io.github.jicklin.starter.ratelimit.processor.builtin;

import io.github.jicklin.starter.ratelimit.processor.ParamValueProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分割值处理器
 * 将字符串按分隔符分割成多个值，每个值作为独立的组别处理
 *
 * @author marry
 */
public class SplitValueProcessor implements ParamValueProcessor {

    public static final String NAME = "split";

    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }

        String stringValue = originalValue.toString();

        // 默认按逗号分割
        String delimiter = getDelimiter(paramName);

        // 分割并清理空值
        List<String> values = Arrays.stream(stringValue.split(delimiter))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        // 如果只有一个值，直接返回字符串
        if (values.size() == 1) {
            return values.get(0);
        }

        // 多个值返回列表
        return values;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 根据参数名确定分隔符
     */
    private String getDelimiter(String paramName) {
        if (paramName == null) {
            return ",";
        }

        String lowerName = paramName.toLowerCase();

        // 根据参数名推断分隔符
        if (lowerName.contains("tag") || lowerName.contains("label")) {
            return ",";  // 标签通常用逗号分割
        } else if (lowerName.contains("path") || lowerName.contains("url")) {
            return "/";  // 路径用斜杠分割
        } else if (lowerName.contains("id") && lowerName.contains("list")) {
            return ",";  // ID列表用逗号分割
        }

        return ",";  // 默认逗号分割
    }
}
