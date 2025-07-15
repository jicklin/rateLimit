# 防重复提交功能使用指南

## 功能概述

基于Redis和AOP的防重复提交功能，通过注解方式简单易用，支持多种配置选项。

## 核心特性

1. **基于注解**: 使用`@PreventDuplicateSubmit`注解即可启用
2. **Redis存储**: 利用Redis的过期机制自动清理
3. **多维度key**: 支持用户、参数、方法等多维度组合
4. **灵活配置**: 可配置时间间隔、提示信息等
5. **用户识别**: 支持多种用户标识提取策略
6. **参数过滤**: 支持排除特定参数

## 快速开始

### 1. 添加依赖

确保项目中包含rate-limit-spring-boot-starter和AOP依赖：

```xml
<dependencies>
    <dependency>
        <groupId>com.marry.starter</groupId>
        <artifactId>rate-limit-spring-boot-starter</artifactId>
        <version>1.3.1-SNAPSHOT</version>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
</dependencies>
```

### 2. 启用功能

在主类上添加`@EnableRateLimit`注解：

```java
@SpringBootApplication
@EnableRateLimit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 使用注解

在需要防重复提交的方法上添加注解：

```java
@RestController
public class OrderController {
    
    @PostMapping("/order/create")
    @PreventDuplicateSubmit(interval = 10, message = "订单创建中，请勿重复提交")
    public Result createOrder(@RequestBody OrderRequest request) {
        // 创建订单逻辑
        return Result.success();
    }
}
```

## 注解参数详解

### @PreventDuplicateSubmit

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| interval | long | 5 | 防重复提交的时间间隔 |
| timeUnit | TimeUnit | SECONDS | 时间单位 |
| message | String | "请勿重复提交" | 提示信息 |
| includeParams | boolean | true | 是否包含请求参数在key中 |
| includeUser | boolean | true | 是否包含用户标识在key中 |
| keyPrefix | String | "" | 自定义key前缀 |
| excludeParams | String[] | {} | 排除的参数名 |

## 使用示例

### 1. 基础用法

```java
@PostMapping("/submit")
@PreventDuplicateSubmit
public Result submit(@RequestBody SubmitRequest request) {
    // 默认5秒内防重复提交
    return processSubmit(request);
}
```

### 2. 自定义时间间隔

```java
@PostMapping("/payment")
@PreventDuplicateSubmit(interval = 30, timeUnit = TimeUnit.SECONDS)
public Result payment(@RequestBody PaymentRequest request) {
    // 30秒内防重复提交
    return processPayment(request);
}
```

### 3. 自定义提示信息

```java
@PostMapping("/order")
@PreventDuplicateSubmit(
    interval = 10, 
    message = "订单正在处理中，请稍后再试"
)
public Result createOrder(@RequestBody OrderRequest request) {
    return createOrder(request);
}
```

### 4. 排除特定参数

```java
@PostMapping("/search")
@PreventDuplicateSubmit(
    interval = 2,
    excludeParams = {"timestamp", "requestId"}
)
public Result search(@RequestBody SearchRequest request) {
    // timestamp和requestId不参与防重复判断
    return doSearch(request);
}
```

### 5. 不区分用户

```java
@PostMapping("/global-action")
@PreventDuplicateSubmit(
    interval = 60,
    includeUser = false,
    message = "系统正在处理中，请稍后再试"
)
public Result globalAction() {
    // 所有用户共享防重复限制
    return processGlobalAction();
}
```

### 6. 不区分参数

```java
@PostMapping("/user-action")
@PreventDuplicateSubmit(
    interval = 5,
    includeParams = false,
    message = "操作过于频繁，请稍后再试"
)
public Result userAction(@RequestBody ActionRequest request) {
    // 同一用户访问此接口就会被限制，不管参数是什么
    return processUserAction(request);
}
```

## 用户标识提取

系统支持多种用户标识提取方式，按优先级排序：

### 1. 默认提取策略

```java
@Component
public class DefaultUserIdentifierExtractor implements UserIdentifierExtractor {
    
    @Override
    public String extractUserIdentifier(HttpServletRequest request) {
        // 1. Authorization header中的token
        // 2. 请求参数中的userId
        // 3. Session中的用户信息
        // 4. 客户端IP地址
    }
}
```

### 2. 自定义用户标识提取器

```java
@Component
@Order(1) // 高优先级
public class JwtUserIdentifierExtractor implements UserIdentifierExtractor {
    
    @Override
    public String extractUserIdentifier(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            // 解析JWT token获取用户ID
            String jwt = token.substring(7);
            return parseUserIdFromJwt(jwt);
        }
        return null;
    }
    
    @Override
    public int getOrder() {
        return 1; // 高优先级
    }
}
```

## 异常处理

### 1. 全局异常处理

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DuplicateSubmitException.class)
    public ResponseEntity<Result> handleDuplicateSubmit(DuplicateSubmitException e) {
        Result result = Result.error(e.getMessage());
        result.put("remainingTime", e.getRemainingTimeInSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
    }
}
```

### 2. 自定义响应

```java
@ExceptionHandler(DuplicateSubmitException.class)
public Result handleDuplicateSubmit(DuplicateSubmitException e) {
    return Result.builder()
        .code(429)
        .message(e.getMessage())
        .data(Map.of(
            "remainingTime", e.getRemainingTimeInSeconds(),
            "retryAfter", e.getRemainingTime()
        ))
        .build();
}
```

## Redis键结构

### 键名格式

```
rate_limit:duplicate_submit:[keyPrefix:]methodSignature[:user:userIdentifier][:params:paramsHash]
```

### 示例

```
# 包含用户和参数
rate_limit:duplicate_submit:OrderController.createOrder:user:token:abc123:params:md5hash

# 只包含用户
rate_limit:duplicate_submit:OrderController.createOrder:user:session:sessionId

# 全局限制
rate_limit:duplicate_submit:OrderController.globalAction

# 自定义前缀
rate_limit:duplicate_submit:order:OrderController.createOrder:user:user123
```

## 配置选项

### 1. Redis配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 2000ms
```

### 2. 日志配置

```yaml
logging:
  level:
    com.marry.starter.ratelimit.aspect.DuplicateSubmitAspect: DEBUG
    com.marry.starter.ratelimit.service.impl.RedisDuplicateSubmitService: DEBUG
```

## 性能考虑

### 1. Redis操作

- 每次请求需要2次Redis操作：检查 + 设置
- 使用Redis的原子操作保证线程安全
- 利用Redis过期机制自动清理

### 2. 参数哈希

- 使用MD5哈希算法生成参数指纹
- TreeMap保证参数顺序一致性
- 排除不必要的参数减少计算开销

### 3. 内存使用

- Redis键会自动过期，不会无限增长
- 参数哈希固定长度，内存可控

## 最佳实践

### 1. 时间间隔设置

```java
// 表单提交：5-10秒
@PreventDuplicateSubmit(interval = 5)

// 支付操作：30-60秒
@PreventDuplicateSubmit(interval = 30)

// 发送短信：60-120秒
@PreventDuplicateSubmit(interval = 60)

// 数据导出：300-600秒
@PreventDuplicateSubmit(interval = 300)
```

### 2. 参数配置

```java
// 查询接口：排除时间戳等变化参数
@PreventDuplicateSubmit(excludeParams = {"timestamp", "requestId", "_t"})

// 用户操作：包含用户标识
@PreventDuplicateSubmit(includeUser = true)

// 系统操作：全局限制
@PreventDuplicateSubmit(includeUser = false, includeParams = false)
```

### 3. 错误处理

```java
// 提供友好的错误信息
@PreventDuplicateSubmit(
    interval = 10,
    message = "订单创建中，请稍后再试"
)

// 在异常处理中返回剩余时间
@ExceptionHandler(DuplicateSubmitException.class)
public Result handleDuplicateSubmit(DuplicateSubmitException e) {
    return Result.error(e.getMessage())
        .put("retryAfter", e.getRemainingTimeInSeconds());
}
```

## 总结

防重复提交功能提供了：

1. ✅ **简单易用**: 一个注解即可启用
2. ✅ **功能完整**: 支持多种配置选项
3. ✅ **性能优秀**: 基于Redis，支持集群
4. ✅ **扩展性强**: 支持自定义用户标识提取器
5. ✅ **生产就绪**: 包含完整的异常处理和日志

通过合理配置，可以有效防止用户重复提交，提升系统稳定性和用户体验。
