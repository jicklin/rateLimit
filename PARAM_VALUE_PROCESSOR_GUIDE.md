# 参数值处理器功能指南

## 功能概述

参数值处理器允许用户在引用项目中自定义参数值的处理逻辑，实现更灵活的参数值提取和转换。这样可以满足不同业务场景下的特殊需求，如数据脱敏、格式标准化、哈希处理等。

## 核心设计

### 1. 处理器接口

```java
public interface ParamValueProcessor {
    /**
     * 处理参数值
     */
    Object processValue(Object originalValue, String paramName, ProcessContext context);
    
    /**
     * 获取处理器名称
     */
    String getName();
}
```

### 2. 处理上下文

```java
public interface ProcessContext {
    String getMethodName();           // 获取方法名
    String getClassName();            // 获取类名
    int getParameterIndex();          // 获取参数索引
    Class<?> getParameterType();      // 获取参数类型
    Object[] getAllParameters();      // 获取所有参数值
    String getUserIdentifier();       // 获取用户标识
    String getRequestPath();          // 获取请求路径
    String getHttpMethod();           // 获取HTTP方法
    Object getAttribute(String key);  // 获取自定义属性
    void setAttribute(String key, Object value); // 设置自定义属性
}
```

### 3. 注解配置

```java
@DuplicateSubmitParam(
    include = true,
    processor = "hash",              // 处理器名称
    processorParams = "length=8",    // 处理器参数
    alias = "hashedValue"
)
String sensitiveData
```

## 内置处理器

### 1. 默认处理器 (default)

```java
@DuplicateSubmitParam(processor = "default")
String normalParam
```

**功能**: 直接返回原始值，不做任何处理
**使用场景**: 默认情况，或明确不需要处理的参数

### 2. 哈希处理器 (hash)

```java
@DuplicateSubmitParam(processor = "hash")
String sensitiveParam
```

**功能**: 将参数值转换为MD5哈希
**使用场景**: 敏感数据处理，需要保护原始值但又要参与防重复校验

### 3. 掩码处理器 (mask)

```java
@DuplicateSubmitParam(processor = "mask")
String phoneNumber
```

**功能**: 对敏感信息进行掩码处理
**识别规则**: 
- password, pwd, secret, token, key
- phone, mobile, email, idcard, bankcard

**处理效果**:
- `13812345678` → `138***78`
- `password123` → `pa***23`

### 4. 标准化处理器 (normalize)

```java
@DuplicateSubmitParam(processor = "normalize")
String userInput
```

**功能**: 标准化处理（去空格、转小写等）
**处理逻辑**:
- 去除前后空格
- 移除多余空格
- 特定字段转小写（email, username, account等）

## 自定义处理器

### 1. 实现处理器接口

```java
@Component
public class CustomOrderProcessor implements ParamValueProcessor {
    
    public static final String NAME = "custom_order";
    
    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }
        
        String stringValue = originalValue.toString();
        
        // 自定义处理逻辑
        if (paramName.toLowerCase().contains("order")) {
            return processOrderNumber(stringValue, context);
        }
        
        return stringValue;
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
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
}
```

### 2. 注册处理器

处理器会通过Spring的自动装配机制自动注册：

```java
@Component  // 自动注册
public class CustomProcessor implements ParamValueProcessor {
    // 实现逻辑
}
```

### 3. 使用自定义处理器

```java
@PostMapping("/order/create")
@PreventDuplicateSubmit(
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED
)
public Result createOrder(
    @DuplicateSubmitParam(processor = "custom_order") String orderNumber,
    @DuplicateSubmitParam(processor = "hash") String userId) {
    
    return processOrder(orderNumber, userId);
}
```

## 处理流程

### 1. 参数处理顺序

```
原始参数值 → 路径提取 → 处理器处理 → 最终值
```

```java
@DuplicateSubmitParam(
    path = "user.profile.email",    // 1. 先进行路径提取
    processor = "normalize",        // 2. 再使用处理器处理
    alias = "userEmail"
)
@RequestBody UserRequest request
```

### 2. 处理上下文传递

```java
// 创建处理上下文
DefaultProcessContext context = new DefaultProcessContext(joinPoint, request, paramIndex, userIdentifier);

// 调用处理器
Object processedValue = processor.processValue(originalValue, paramName, context);
```

### 3. 错误处理

```java
try {
    Object result = processor.processValue(originalValue, paramName, context);
    return result;
} catch (Exception e) {
    logger.error("参数值处理异常: processor={}, param={}", processorName, paramName, e);
    // 异常时返回原始值
    return originalValue;
}
```

## 使用示例

### 1. 基础使用

```java
@PostMapping("/user/register")
@PreventDuplicateSubmit(
    interval = 10,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED
)
public Result registerUser(
    @DuplicateSubmitParam(processor = "normalize", alias = "email") String userEmail,
    @DuplicateSubmitParam(processor = "hash", alias = "pwd") String password,
    @DuplicateSubmitParam(processor = "mask", alias = "phone") String phoneNumber) {
    
    return userService.register(userEmail, password, phoneNumber);
}
```

### 2. 组合使用

```java
@PostMapping("/order/payment")
@PreventDuplicateSubmit(
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result processPayment(
    @DuplicateSubmitParam(group = "order", processor = "custom_order") String orderNumber,
    @DuplicateSubmitParam(group = "payment", processor = "hash") String paymentToken,
    @DuplicateSubmitParam(group = "user", processor = "normalize") String userAccount) {
    
    return paymentService.process(orderNumber, paymentToken, userAccount);
}
```

### 3. 高级用法

```java
@Component
public class AdvancedProcessor implements ParamValueProcessor {
    
    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        // 根据请求路径使用不同处理逻辑
        String requestPath = context.getRequestPath();
        if (requestPath.contains("/admin/")) {
            return processAdminValue(originalValue, context);
        }
        
        // 根据用户类型使用不同处理逻辑
        String userIdentifier = context.getUserIdentifier();
        if (userIdentifier.startsWith("VIP_")) {
            return processVipValue(originalValue, context);
        }
        
        return originalValue;
    }
    
    private Object processAdminValue(Object value, ProcessContext context) {
        // 管理员请求的特殊处理
        return value;
    }
    
    private Object processVipValue(Object value, ProcessContext context) {
        // VIP用户的特殊处理
        return value;
    }
}
```

## 最佳实践

### 1. 处理器命名

```java
// ✅ 推荐：使用描述性名称
public static final String NAME = "order_normalize";
public static final String NAME = "sensitive_hash";
public static final String NAME = "user_mask";

// ❌ 避免：使用模糊名称
public static final String NAME = "proc1";
public static final String NAME = "handler";
```

### 2. 错误处理

```java
// ✅ 推荐：完善的错误处理
@Override
public Object processValue(Object originalValue, String paramName, ProcessContext context) {
    try {
        if (originalValue == null) {
            return null;
        }
        
        // 处理逻辑
        return processLogic(originalValue, context);
        
    } catch (Exception e) {
        logger.error("处理器异常: {}", getName(), e);
        return originalValue; // 返回原始值
    }
}
```

### 3. 性能考虑

```java
// ✅ 推荐：缓存重复计算的结果
private final Map<String, String> hashCache = new ConcurrentHashMap<>();

@Override
public Object processValue(Object originalValue, String paramName, ProcessContext context) {
    String stringValue = originalValue.toString();
    
    // 使用缓存避免重复计算
    return hashCache.computeIfAbsent(stringValue, this::computeHash);
}
```

### 4. 测试覆盖

```java
@Test
public void testCustomProcessor() {
    CustomProcessor processor = new CustomProcessor();
    
    // 测试正常情况
    Object result = processor.processValue("ORD123", "orderNumber", mockContext);
    assertEquals("USER_0000000123", result);
    
    // 测试边界情况
    Object nullResult = processor.processValue(null, "orderNumber", mockContext);
    assertNull(nullResult);
    
    // 测试异常情况
    Object errorResult = processor.processValue(invalidValue, "orderNumber", mockContext);
    assertEquals(invalidValue, errorResult); // 应该返回原始值
}
```

## 配置管理

### 1. 处理器注册

```java
@Configuration
public class ProcessorConfig {
    
    @Bean
    public ParamValueProcessor customBusinessProcessor() {
        return new CustomBusinessProcessor();
    }
    
    @Bean
    public ParamValueProcessor industrySpecificProcessor() {
        return new IndustrySpecificProcessor();
    }
}
```

### 2. 条件注册

```java
@Component
@ConditionalOnProperty(name = "app.features.advanced-processing", havingValue = "true")
public class AdvancedProcessor implements ParamValueProcessor {
    // 只在特定配置下启用
}
```

### 3. 处理器监控

```java
@Component
public class ProcessorMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordProcessorUsage(String processorName, long duration) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("processor.usage")
            .tag("processor", processorName)
            .register(meterRegistry));
    }
}
```

## 总结

参数值处理器功能提供了：

1. ✅ **灵活性**: 支持自定义参数值处理逻辑
2. ✅ **扩展性**: 可在引用项目中轻松添加新处理器
3. ✅ **内置支持**: 提供常用的内置处理器
4. ✅ **上下文丰富**: 提供完整的处理上下文信息
5. ✅ **错误安全**: 处理器异常时不影响主流程
6. ✅ **性能友好**: 支持缓存和性能优化

这使得防重复提交功能能够适应更复杂和多样化的业务需求。
