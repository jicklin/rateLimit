# 参数级别控制使用指南

## 功能概述

新增的参数级别控制功能允许开发者精确控制哪些方法参数参与防重复提交key的生成，提供了比传统`includeParams`和`excludeParams`更加灵活和直观的控制方式。

## 核心注解

### 1. @DuplicateSubmitParam

用于标记方法参数，控制该参数是否参与key生成。

```java
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DuplicateSubmitParam {
    boolean include() default true;    // 是否包含此参数
    String alias() default "";         // 参数别名
    String path() default "";          // 属性提取路径
}
```

### 2. @DuplicateSubmitIgnore

便捷的排除注解，等同于`@DuplicateSubmitParam(include = false)`。

```java
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DuplicateSubmitIgnore {
    String reason() default "";        // 忽略原因说明
}
```

### 3. ParamStrategy枚举

在`@PreventDuplicateSubmit`中新增的参数处理策略：

```java
public enum ParamStrategy {
    INCLUDE_ALL,        // 包含所有参数（默认）
    INCLUDE_ANNOTATED,  // 只包含被@DuplicateSubmitParam标注的参数
    EXCLUDE_ANNOTATED,  // 排除被@DuplicateSubmitIgnore标注的参数
    EXCLUDE_ALL         // 不包含任何参数
}
```

## 使用示例

### 1. 只包含标注的参数

```java
@PostMapping("/order/create")
@PreventDuplicateSubmit(
    interval = 10,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    message = "订单创建中，请勿重复提交"
)
public Result createOrder(
        @DuplicateSubmitParam(include = true, alias = "orderId") String orderNumber,
        @DuplicateSubmitParam(include = true, alias = "userId") String userCode,
        @DuplicateSubmitIgnore String timestamp,
        String requestId,
        @RequestBody OrderRequest request) {
    
    // 只有orderNumber和userCode参与key生成
    // timestamp、requestId、request都被排除
    return processOrder(orderNumber, userCode, request);
}
```

**生成的key包含**：
- `orderNumber`（别名为orderId）
- `userCode`（别名为userId）

**排除的参数**：
- `timestamp`（被@DuplicateSubmitIgnore标注）
- `requestId`（未被@DuplicateSubmitParam标注）
- `request`（未被@DuplicateSubmitParam标注）

### 2. 排除标注的参数

```java
@PostMapping("/user/update")
@PreventDuplicateSubmit(
    interval = 5,
    paramStrategy = ParamStrategy.EXCLUDE_ANNOTATED,
    message = "用户信息更新中，请勿重复提交"
)
public Result updateUser(
        String userId,
        String userName,
        @DuplicateSubmitIgnore(reason = "时间戳会变化") String timestamp,
        @DuplicateSubmitIgnore(reason = "请求ID每次不同") String requestId,
        @RequestBody UserRequest request) {
    
    // 包含userId、userName、request
    // 排除timestamp、requestId
    return updateUser(userId, userName, request);
}
```

**生成的key包含**：
- `userId`
- `userName`
- `request`

**排除的参数**：
- `timestamp`（被@DuplicateSubmitIgnore标注）
- `requestId`（被@DuplicateSubmitIgnore标注）

### 3. 对象属性提取

```java
@PostMapping("/payment/process")
@PreventDuplicateSubmit(
    interval = 30,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    message = "支付处理中，请勿重复提交"
)
public Result processPayment(
        @DuplicateSubmitParam(include = true, path = "orderId", alias = "order") 
        @RequestBody PaymentRequest paymentRequest,
        
        @DuplicateSubmitParam(include = true, path = "user.id", alias = "userId") 
        @RequestBody UserContext userContext,
        
        @DuplicateSubmitIgnore String sessionId) {
    
    // 从paymentRequest中提取orderId
    // 从userContext.user中提取id
    // 排除sessionId
    return processPayment(paymentRequest, userContext);
}
```

**生成的key包含**：
- `paymentRequest.orderId`（别名为order）
- `userContext.user.id`（别名为userId）

**排除的参数**：
- `sessionId`（被@DuplicateSubmitIgnore标注）

### 4. 不包含任何参数

```java
@PostMapping("/global/action")
@PreventDuplicateSubmit(
    interval = 60,
    paramStrategy = ParamStrategy.EXCLUDE_ALL,
    includeUser = false,
    message = "系统操作进行中，请稍后再试"
)
public Result globalAction(
        String param1,
        String param2,
        @RequestBody ActionRequest request) {
    
    // 所有参数都被排除，只基于方法名生成key
    // 全局限制，所有用户共享
    return processGlobalAction(param1, param2, request);
}
```

**生成的key包含**：
- 只有方法签名

**排除的参数**：
- 所有参数都被排除

## 路径表达式

### 1. 简单属性访问

```java
@DuplicateSubmitParam(include = true, path = "orderId")
@RequestBody OrderRequest request
```

从`request.orderId`提取值。

### 2. 嵌套属性访问

```java
@DuplicateSubmitParam(include = true, path = "user.profile.id")
@RequestBody UserRequest request
```

从`request.user.profile.id`提取值。

### 3. 支持的访问方式

- **Getter方法**：`getOrderId()`, `isActive()`
- **直接字段访问**：`public String orderId`
- **继承字段**：父类中的字段

### 4. 错误处理

如果路径提取失败，会：
1. 记录警告日志
2. 返回原始对象
3. 不影响防重复提交功能

## 策略对比

### 1. INCLUDE_ALL（默认策略）

```java
@PreventDuplicateSubmit(interval = 5)
public Result method(String a, String b, @DuplicateSubmitIgnore String c) {
    // 包含：a, b
    // 排除：c（被@DuplicateSubmitIgnore标注）
}
```

### 2. INCLUDE_ANNOTATED

```java
@PreventDuplicateSubmit(
    interval = 5,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED
)
public Result method(
        @DuplicateSubmitParam String a, 
        String b, 
        @DuplicateSubmitParam String c) {
    // 包含：a, c
    // 排除：b（未被@DuplicateSubmitParam标注）
}
```

### 3. EXCLUDE_ANNOTATED

```java
@PreventDuplicateSubmit(
    interval = 5,
    paramStrategy = ParamStrategy.EXCLUDE_ANNOTATED
)
public Result method(
        String a, 
        @DuplicateSubmitIgnore String b, 
        String c) {
    // 包含：a, c
    // 排除：b（被@DuplicateSubmitIgnore标注）
}
```

### 4. EXCLUDE_ALL

```java
@PreventDuplicateSubmit(
    interval = 5,
    paramStrategy = ParamStrategy.EXCLUDE_ALL
)
public Result method(String a, String b, String c) {
    // 包含：无
    // 排除：a, b, c（所有参数）
}
```

## 最佳实践

### 1. 选择合适的策略

```java
// 大部分参数都需要包含，少数需要排除
@PreventDuplicateSubmit(paramStrategy = ParamStrategy.EXCLUDE_ANNOTATED)

// 只有少数参数需要包含
@PreventDuplicateSubmit(paramStrategy = ParamStrategy.INCLUDE_ANNOTATED)

// 不关心参数，只基于用户和方法
@PreventDuplicateSubmit(paramStrategy = ParamStrategy.EXCLUDE_ALL)
```

### 2. 使用有意义的别名

```java
@DuplicateSubmitParam(include = true, alias = "orderId")
String orderNumber  // 在key中显示为orderId而不是orderNumber
```

### 3. 提供排除原因

```java
@DuplicateSubmitIgnore(reason = "时间戳每次都不同")
String timestamp

@DuplicateSubmitIgnore(reason = "请求ID用于追踪，不影响业务")
String requestId
```

### 4. 合理使用路径提取

```java
// 只提取关键业务字段
@DuplicateSubmitParam(include = true, path = "orderId", alias = "order")
@RequestBody ComplexRequest request

// 避免提取可能变化的字段
@DuplicateSubmitParam(include = true, path = "user.id")  // ✅ 用户ID稳定
// 不要这样做：
// @DuplicateSubmitParam(include = true, path = "user.lastLoginTime")  // ❌ 会变化
```

## 性能考虑

### 1. 路径提取开销

- 简单属性访问：开销很小
- 深层嵌套访问：开销稍大，但可接受
- 建议嵌套层级不超过3层

### 2. 参数数量

- 包含的参数越多，key生成开销越大
- 建议只包含真正影响业务的参数
- 排除变化频繁的参数（如时间戳）

### 3. 对象序列化

- 复杂对象会被转换为字符串
- 建议使用路径提取具体字段
- 避免包含大对象

## 迁移指南

### 从旧版本迁移

**旧版本**：
```java
@PreventDuplicateSubmit(
    interval = 5,
    includeParams = true,
    excludeParams = {"timestamp", "requestId"}
)
```

**新版本**：
```java
@PreventDuplicateSubmit(
    interval = 5,
    paramStrategy = ParamStrategy.EXCLUDE_ANNOTATED
)
public Result method(
        String param1,
        String param2,
        @DuplicateSubmitIgnore String timestamp,
        @DuplicateSubmitIgnore String requestId) {
}
```

### 兼容性

- 旧的`includeParams`和`excludeParams`属性已移除
- 默认策略`INCLUDE_ALL`与旧版本`includeParams=true`行为一致
- 需要更新代码以使用新的注解方式

## 总结

参数级别控制功能提供了：

1. ✅ **精确控制**：通过注解精确控制每个参数
2. ✅ **灵活策略**：四种策略满足不同场景需求
3. ✅ **路径提取**：支持从复杂对象中提取特定字段
4. ✅ **别名支持**：提供有意义的参数名称
5. ✅ **文档化**：通过注解自文档化参数用途

这种方式比传统的字符串数组配置更加类型安全、IDE友好，并且提供了更强的表达能力。
