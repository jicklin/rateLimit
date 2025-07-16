package com.marry.starter.ratelimit.processor.builtin;

import com.marry.starter.ratelimit.processor.ParamValueProcessor;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 哈希值处理器
 * 将参数值转换为MD5哈希
 * 
 * @author marry
 */
public class HashValueProcessor implements ParamValueProcessor {

    public static final String NAME = "hash";

    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }
        
        String stringValue = originalValue.toString();
        return DigestUtils.md5DigestAsHex(stringValue.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getName() {
        return NAME;
    }
}
