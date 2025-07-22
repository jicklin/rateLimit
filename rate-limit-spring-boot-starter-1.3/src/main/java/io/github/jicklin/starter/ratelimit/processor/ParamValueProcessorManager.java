package io.github.jicklin.starter.ratelimit.processor;

import io.github.jicklin.starter.ratelimit.processor.builtin.DefaultValueProcessor;
import io.github.jicklin.starter.ratelimit.processor.builtin.HashValueProcessor;
import io.github.jicklin.starter.ratelimit.processor.builtin.MaskValueProcessor;
import io.github.jicklin.starter.ratelimit.processor.builtin.NormalizeValueProcessor;
import io.github.jicklin.starter.ratelimit.processor.builtin.SplitValueProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数值处理器管理器
 * 负责管理和调用参数值处理器
 *
 * @author marry
 */
@Component
public class ParamValueProcessorManager {

    private static final Logger logger = LoggerFactory.getLogger(ParamValueProcessorManager.class);

    private final Map<String, ParamValueProcessor> processors = new HashMap<>();

    @Autowired(required = false)
    private List<ParamValueProcessor> customProcessors;

    @PostConstruct
    public void init() {
        // 注册内置处理器
        registerBuiltinProcessors();

        // 注册自定义处理器
        registerCustomProcessors();

        logger.debug("参数值处理器管理器初始化完成，共注册{}个处理器: {}",
            processors.size(), processors.keySet());
    }

    /**
     * 注册内置处理器
     */
    private void registerBuiltinProcessors() {
        registerProcessor(new DefaultValueProcessor());
        registerProcessor(new HashValueProcessor());
        registerProcessor(new MaskValueProcessor());
        registerProcessor(new NormalizeValueProcessor());
        registerProcessor(new SplitValueProcessor());
    }

    /**
     * 注册自定义处理器
     */
    private void registerCustomProcessors() {
        if (customProcessors != null) {
            for (ParamValueProcessor processor : customProcessors) {
                registerProcessor(processor);
            }
        }
    }

    /**
     * 注册处理器
     */
    public void registerProcessor(ParamValueProcessor processor) {
        if (processor == null) {
            logger.warn("尝试注册空的参数值处理器");
            return;
        }

        String name = processor.getName();
        if (name == null || name.trim().isEmpty()) {
            logger.warn("参数值处理器名称不能为空: {}", processor.getClass().getName());
            return;
        }

        if (processors.containsKey(name)) {
            logger.warn("参数值处理器名称冲突，覆盖原有处理器: {}", name);
        }

        processors.put(name, processor);
        logger.debug("注册参数值处理器: {} -> {}", name, processor.getClass().getName());
    }

    /**
     * 获取处理器
     */
    public ParamValueProcessor getProcessor(String name) {
        if (name == null || name.trim().isEmpty()) {
            return processors.get(DefaultValueProcessor.NAME);
        }

        ParamValueProcessor processor = processors.get(name);
        if (processor == null) {
            logger.warn("未找到参数值处理器: {}，使用默认处理器", name);
            return processors.get(DefaultValueProcessor.NAME);
        }

        return processor;
    }

    /**
     * 处理参数值
     */
    public Object processValue(String processorName, Object originalValue, String paramName,
                             ParamValueProcessor.ProcessContext context) {
        try {
            ParamValueProcessor processor = getProcessor(processorName);
            Object result = processor.processValue(originalValue, paramName, context);

            logger.debug("参数值处理: processor={}, param={}, original={}, result={}, isCollection={}",
                processorName, paramName, originalValue, result, isCollection(result));

            return result;
        } catch (Exception e) {
            logger.error("参数值处理异常: processor={}, param={}, value={}",
                processorName, paramName, originalValue, e);

            // 异常时返回原始值
            return originalValue;
        }
    }

    /**
     * 判断结果是否为集合
     */
    public boolean isCollection(Object value) {
        return value instanceof java.util.Collection || value instanceof Object[];
    }

    /**
     * 将集合转换为列表
     */
    public java.util.List<Object> toList(Object value) {
        if (value instanceof java.util.Collection) {
            return new java.util.ArrayList<>((java.util.Collection<?>) value);
        } else if (value instanceof Object[]) {
            return java.util.Arrays.asList((Object[]) value);
        } else {
            return java.util.Collections.singletonList(value);
        }
    }

    /**
     * 获取所有处理器名称
     */
    public String[] getProcessorNames() {
        return processors.keySet().toArray(new String[0]);
    }

    /**
     * 检查处理器是否存在
     */
    public boolean hasProcessor(String name) {
        return processors.containsKey(name);
    }
}
