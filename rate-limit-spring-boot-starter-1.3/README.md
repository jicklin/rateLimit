# Rate Limit Spring Boot Starter

基于Redis和令牌桶算法的分布式限流Spring Boot Starter，支持IP、用户ID、路径等多维度限流。

## 功能特性

- **多维度限流**: 支持路径、IP、用户ID等多维度限流
- **令牌桶算法**: 基于Redis的分布式令牌桶实现，支持突发流量
- **Ant路径匹配**: 支持`?`、`*`、`**`通配符的路径模式匹配
- **HTTP方法过滤**: 可选择性地对特定HTTP方法进行限流
- **自动配置**: 开箱即用的Spring Boot自动配置
- **统计功能**: 提供详细的限流统计信息
- **版本兼容**: 支持SpringBoot 2.0+版本，自动检测版本兼容性

## 版本兼容性

| SpringBoot版本 | 兼容性 | 说明 | 推荐使用版本 |
|--------------|------|------|------------|
| 1.3.x        | ⚠️ 有限支持 | 基本功能可用 | 1.0.0-springboot-1-3-8 |
| 1.5.x        | ⚠️ 有限支持 | 基本功能可用 | 1.0.0-springboot-1-5-22 |
| 2.0.x - 2.3.x | ✅ 完全兼容 | 推荐版本 | 1.0.0-springboot-2-0-9 |
| 2.4.x - 2.6.x | ✅ 完全兼容 | 推荐版本 | 1.0.0-springboot-2-6-13 |
| 2.7.x        | ✅ 完全兼容 | 推荐版本 | 1.0.0-springboot-2-7-8 |
| 3.0.x+       | ⚠️ 部分兼容 | 需要Java 17+ | 1.0.0（通用版本） |

详细的版本兼容性信息请参考 [VERSION_COMPATIBILITY.md](VERSION_COMPATIBILITY.md)

## 快速开始

### 1. 添加依赖

根据您的SpringBoot版本选择对应的starter版本：

#### SpringBoot 2.6.x 项目（推荐）
```xml
<dependency>
    <groupId>com.marry</groupId>
    <artifactId>rate-limit-spring-boot-starter</artifactId>
    <version>1.0.0-springboot-2-6-13</version>
</dependency>
```

#### SpringBoot 2.7.x 项目
```xml
<dependency>
    <groupId>com.marry</groupId>
    <artifactId>rate-limit-spring-boot-starter</artifactId>
    <version>1.0.0-springboot-2-7-8</version>
</dependency>
```

#### SpringBoot 1.3.x 项目（老版本）
```xml
<dependency>
    <groupId>com.marry</groupId>
    <artifactId>rate-limit-spring-boot-starter</artifactId>
    <version>1.0.0-springboot-1-3-8</version>
</dependency>
```

#### 通用版本（兼容性最好）
```xml
<dependency>
    <groupId>com.marry</groupId>
    <artifactId>rate-limit-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**版本选择建议**:
- 优先使用与您SpringBoot版本匹配的专用版本
- 如果没有匹配的版本，使用通用版本（1.0.0）
- 通用版本使用provided依赖，兼容性最好但需要项目提供相关依赖

**注意**: 如果遇到依赖冲突，可以排除冲突的依赖：

```xml
<dependency>
    <groupId>com.marry</groupId>
    <artifactId>rate-limit-spring-boot-starter</artifactId>
    <version>1.0.0-springboot-2-6-13</version>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### 2. 启用限流功能

在Spring Boot主类上添加`@EnableRateLimit`注解：

```java
@SpringBootApplication
@EnableRateLimit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

或者在配置类上添加：

```java
@Configuration
@EnableRateLimit
public class RateLimitConfig {
}
```

### 3. 配置Redis

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

### 4. 配置限流参数（可选）

```yaml
rate-limit:
  enabled: true
  redis-key-prefix: rate_limit
  default-bucket-capacity: 10
  default-refill-rate: 5
  default-time-window: 1
  
  # 拦截器配置
  interceptor:
    enabled: true
    path-patterns:
      - "/**"
    exclude-path-patterns:
      - "/static/**"
      - "/css/**"
      - "/js/**"
      - "/images/**"
      - "/favicon.ico"
    order: 0
  
  # 统计配置
  stats:
    enabled: true
    retention-hours: 24
    realtime-window-minutes: 15
```

## @EnableRateLimit注解说明

`@EnableRateLimit`注解用于启用限流功能，具体的配置参数通过`application.yml`配置文件进行设置。

### 5. 编程式使用

```java
@RestController
public class TestController {
    
    @Autowired
    private RateLimitConfigService configService;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @PostMapping("/api/rules")
    public String createRule() {
        // 创建限流规则
        RateLimitRule rule = new RateLimitRule();
        rule.setName("API接口限流");
        rule.setPathPattern("/api/**");
        rule.setBucketCapacity(20);
        rule.setRefillRate(10);
        rule.setTimeWindow(1);
        rule.setEnabled(true);
        rule.setEnableIpLimit(true);
        rule.setIpRequestLimit(5);
        
        configService.saveRule(rule);
        return "规则创建成功";
    }
    
    @GetMapping("/api/test")
    public String testApi(HttpServletRequest request) {
        // 手动检查限流
        if (!rateLimitService.isAllowed(request)) {
            throw new RuntimeException("请求过于频繁");
        }
        
        return "请求成功";
    }
}
```

## 配置说明

### 基础配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rate-limit.enabled` | `true` | 是否启用限流功能 |
| `rate-limit.redis-key-prefix` | `rate_limit` | Redis键前缀 |
| `rate-limit.default-bucket-capacity` | `10` | 默认令牌桶容量 |
| `rate-limit.default-refill-rate` | `5` | 默认令牌补充速率 |
| `rate-limit.default-time-window` | `1` | 默认时间窗口（秒） |

### 拦截器配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rate-limit.interceptor.enabled` | `true` | 是否启用拦截器 |
| `rate-limit.interceptor.path-patterns` | `["/**"]` | 拦截路径模式 |
| `rate-limit.interceptor.exclude-path-patterns` | 见配置文件 | 排除路径模式 |
| `rate-limit.interceptor.order` | `0` | 拦截器顺序 |

### 统计配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rate-limit.stats.enabled` | `true` | 是否启用统计功能 |
| `rate-limit.stats.retention-hours` | `24` | 统计数据保留时间 |
| `rate-limit.stats.realtime-window-minutes` | `15` | 实时统计时间窗口 |

## API接口

### 限流规则管理

```java
// 保存规则
RateLimitRule saveRule(RateLimitRule rule);

// 获取规则
RateLimitRule getRule(String ruleId);

// 获取所有规则
List<RateLimitRule> getAllRules();

// 获取启用的规则
List<RateLimitRule> getEnabledRules();

// 删除规则
void deleteRule(String ruleId);

// 启用/禁用规则
void toggleRule(String ruleId, boolean enabled);
```

### 限流检查

```java
// 检查请求是否被限流
boolean isAllowed(HttpServletRequest request);

// 检查指定规则
boolean isAllowed(HttpServletRequest request, RateLimitRule rule);

// 获取剩余令牌数
long getRemainingTokens(HttpServletRequest request, RateLimitRule rule);

// 重置限流状态
void reset(HttpServletRequest request, RateLimitRule rule);
```

### 统计信息

```java
// 获取统计信息
RateLimitStats getStats(String ruleId);

// 获取所有统计信息
List<RateLimitStats> getAllStats();

// 获取全局统计信息
Map<String, Object> getGlobalStats();

// 重置统计信息
void resetStats(String ruleId);
```

## 限流规则配置

### 基本配置

```java
RateLimitRule rule = new RateLimitRule();
rule.setName("API接口限流");
rule.setDescription("对所有API接口进行限流");
rule.setPathPattern("/api/**");
rule.setHttpMethods(Arrays.asList(HttpMethod.GET, HttpMethod.POST));
rule.setBucketCapacity(20);
rule.setRefillRate(10);
rule.setTimeWindow(1);
rule.setEnabled(true);
rule.setPriority(100);
```

### IP维度限流

```java
rule.setEnableIpLimit(true);
rule.setIpRequestLimit(5);
rule.setIpBucketCapacity(10);
```

### 用户维度限流

```java
rule.setEnableUserLimit(true);
rule.setUserRequestLimit(3);
rule.setUserBucketCapacity(5);
```

## 路径模式

支持Ant风格的路径模式：

- `?` - 匹配单个字符
- `*` - 匹配任意字符（除路径分隔符）
- `**` - 匹配任意路径

示例：
- `/api/*` - 匹配 `/api/users`，不匹配 `/api/users/123`
- `/api/**` - 匹配 `/api/users` 和 `/api/users/123`
- `/user/*/profile` - 匹配 `/user/123/profile`

## 扩展开发

### 自定义限流策略

```java
@Component
public class CustomRateLimitStrategy implements RateLimitStrategy {
    
    @Override
    public String generateKey(HttpServletRequest request, RateLimitRule rule) {
        // 自定义键生成逻辑
        return "custom:" + rule.getId() + ":" + extractIdentifier(request);
    }
    
    @Override
    public Integer getRequestLimit(RateLimitRule rule) {
        // 自定义请求限制逻辑
        return rule.getRefillRate();
    }
    
    @Override
    public boolean supports(RateLimitRule rule) {
        // 自定义支持条件
        return true;
    }
    
    @Override
    public String extractIdentifier(HttpServletRequest request) {
        // 自定义标识符提取逻辑
        return request.getHeader("Custom-Id");
    }
}
```

## 注意事项

1. 确保Redis服务正常运行
2. 合理设置令牌桶容量和补充速率
3. 注意路径模式的匹配规则
4. 监控限流统计信息，及时调整规则
5. 在高并发场景下，建议使用Redis集群

## 许可证

MIT License
