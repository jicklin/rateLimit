# @EnableRateLimit注解使用示例

本文档展示如何使用`@EnableRateLimit`注解来启用限流功能。

## 基本使用

### 1. 在主类上使用

```java
@SpringBootApplication
@EnableRateLimit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 2. 在配置类上使用

```java
@Configuration
@EnableRateLimit
public class RateLimitConfig {
}
```

## 配置说明

`@EnableRateLimit`注解只是用来启用限流功能，具体的配置参数需要通过`application.yml`配置文件进行设置。

### 基本配置

```yaml
rate-limit:
  enabled: true                    # 是否启用限流功能
  redis-key-prefix: rate_limit     # Redis键前缀

  # 拦截器配置
  interceptor:
    enabled: true                  # 是否启用拦截器
    path-patterns:
      - "/**"                      # 拦截路径模式
    exclude-path-patterns:
      - "/static/**"               # 排除路径模式
      - "/health"
    order: 0                       # 拦截器顺序

  # 统计配置
  stats:
    enabled: true                  # 是否启用统计功能
    retention-hours: 24            # 统计数据保留时间
```

## 配置组合示例

### 1. 完整功能配置

```java
@SpringBootApplication
@EnableRateLimit
public class FullFeatureApplication {
    public static void main(String[] args) {
        SpringApplication.run(FullFeatureApplication.class, args);
    }
}
```

```yaml
rate-limit:
  enabled: true
  redis-key-prefix: my_app_rate_limit
  interceptor:
    enabled: true
    exclude-path-patterns:
      - "/static/**"
      - "/health"
  stats:
    enabled: true
    retention-hours: 24
```

**特点：**
- 自动拦截请求
- 记录统计信息
- 使用自定义Redis键前缀

### 2. 手动控制配置

```java
@SpringBootApplication
@EnableRateLimit
public class ManualControlApplication {
    public static void main(String[] args) {
        SpringApplication.run(ManualControlApplication.class, args);
    }
}

@RestController
public class ApiController {

    @Autowired
    private RateLimitService rateLimitService;

    @GetMapping("/api/data")
    public ResponseEntity<String> getData(HttpServletRequest request) {
        // 手动检查限流
        if (!rateLimitService.isAllowed(request)) {
            return ResponseEntity.status(429).body("请求过于频繁");
        }

        return ResponseEntity.ok("数据");
    }
}
```

```yaml
rate-limit:
  enabled: true
  interceptor:
    enabled: false  # 禁用自动拦截
  stats:
    enabled: true
```

**特点：**
- 不自动拦截请求
- 需要手动调用限流检查
- 记录统计信息
- 适合需要精细控制的场景

### 3. 轻量级配置

```java
@SpringBootApplication
@EnableRateLimit
public class LightweightApplication {
    public static void main(String[] args) {
        SpringApplication.run(LightweightApplication.class, args);
    }
}
```

```yaml
rate-limit:
  enabled: true
  interceptor:
    enabled: true
  stats:
    enabled: false  # 禁用统计功能
```

**特点：**
- 自动拦截请求
- 不记录统计信息
- 减少Redis存储开销
- 适合只需要限流功能的场景

### 4. 多应用部署配置

```java
// 应用A
@SpringBootApplication
@EnableRateLimit
public class ApplicationA {
    public static void main(String[] args) {
        SpringApplication.run(ApplicationA.class, args);
    }
}

// 应用B
@SpringBootApplication
@EnableRateLimit
public class ApplicationB {
    public static void main(String[] args) {
        SpringApplication.run(ApplicationB.class, args);
    }
}
```

```yaml
# 应用A的配置
rate-limit:
  redis-key-prefix: app_a_rate_limit

# 应用B的配置
rate-limit:
  redis-key-prefix: app_b_rate_limit
```

**特点：**
- 不同应用使用不同的Redis键前缀
- 避免多应用间的键冲突
- 适合微服务架构

## 与配置文件结合使用

注解配置和配置文件可以结合使用：

```java
@SpringBootApplication
@EnableRateLimit(enableInterceptor = true)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

```yaml
rate-limit:
  interceptor:
    enabled: true  # 与注解配置结合
    exclude-path-patterns:
      - "/static/**"
      - "/health"
    order: 0
  stats:
    retention-hours: 24
```

## 注意事项

1. **注解优先级**: 注解参数会影响自动配置的行为
2. **配置文件补充**: 配置文件可以提供更详细的配置选项
3. **Redis依赖**: 确保Redis配置正确，否则启动会失败
4. **单一注解**: 一个应用中只需要一个`@EnableRateLimit`注解
5. **位置灵活**: 注解可以放在主类或任何配置类上

## 迁移指南

### 从spring.factories方式迁移

**之前（自动配置）：**
```java
@SpringBootApplication
public class Application {
    // 自动配置，无需额外代码
}
```

**现在（注解方式）：**
```java
@SpringBootApplication
@EnableRateLimit  // 显式启用
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**优势：**
- 更明确的控制
- 避免意外的自动配置
- 更好的可读性
- 灵活的参数配置
