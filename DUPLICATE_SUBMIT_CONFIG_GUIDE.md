# 防重复提交配置指南

## 配置概述

防重复提交功能现在拥有独立的配置类和自动配置，提供了丰富的配置选项来满足不同场景的需求。

## 配置结构

### 1. 配置类层次结构

```
DuplicateSubmitAutoConfiguration (自动配置类)
├── DuplicateSubmitProperties (配置属性类)
│   ├── ParamConfig (参数处理配置)
│   ├── UserConfig (用户标识提取配置)
│   └── PerformanceConfig (性能配置)
└── DuplicateSubmitConfigInfo (配置信息类)
```

### 2. 配置前缀

所有防重复提交相关配置都使用统一前缀：`rate-limit.duplicate-submit`

## 基础配置

### 1. 启用/禁用功能

```yaml
rate-limit:
  duplicate-submit:
    # 是否启用防重复提交功能
    enabled: true
```

### 2. 基本参数

```yaml
rate-limit:
  duplicate-submit:
    # 默认防重复提交间隔（秒）
    default-interval: 5
    
    # 默认提示信息
    default-message: "请勿重复提交"
    
    # Redis key前缀
    key-prefix: "duplicate_submit"
```

### 3. 功能开关

```yaml
rate-limit:
  duplicate-submit:
    # 是否启用性能监控
    enable-metrics: false
    
    # 是否启用详细日志
    enable-detail-log: false
```

## 高级配置

### 1. 锁值生成策略

```yaml
rate-limit:
  duplicate-submit:
    # 锁值生成策略
    lock-value-strategy: THREAD_TIME_NANO
```

**可选值**：
- `THREAD_TIME_NANO`: 线程ID + 时间戳 + 纳秒时间（默认，最安全）
- `UUID`: 使用UUID生成（性能稍差，但全局唯一）
- `THREAD_TIME`: 线程ID + 时间戳（性能最好，但并发冲突概率稍高）
- `CUSTOM`: 自定义策略（需要实现相应接口）

### 2. 参数处理配置

```yaml
rate-limit:
  duplicate-submit:
    param:
      # 最大参数深度（对象属性提取）
      max-depth: 3
      
      # 是否缓存参数哈希
      cache-hash: true
      
      # 参数序列化最大长度
      max-serialized-length: 1024
      
      # 是否忽略null值参数
      ignore-null-values: true
```

**配置说明**：
- `max-depth`: 控制对象属性提取的最大深度，避免过深的嵌套
- `cache-hash`: 是否缓存参数哈希值，提升性能
- `max-serialized-length`: 限制参数序列化后的最大长度
- `ignore-null-values`: 是否忽略null值参数

### 3. 用户标识提取配置

```yaml
rate-limit:
  duplicate-submit:
    user:
      # 用户标识提取顺序
      extract-order: 
        - "authorization"
        - "userId" 
        - "session"
        - "ip"
      
      # Authorization header名称
      authorization-header: "Authorization"
      
      # 用户ID参数名
      user-id-param: "userId"
      
      # Session中用户信息的key
      session-user-key: "user"
      
      # 是否使用IP作为最后的fallback
      use-ip-fallback: true
```

**配置说明**：
- `extract-order`: 定义用户标识提取的优先级顺序
- `authorization-header`: 自定义Authorization header名称
- `user-id-param`: 自定义用户ID参数名
- `session-user-key`: 自定义Session中用户信息的key
- `use-ip-fallback`: 当其他方式都失败时，是否使用IP作为用户标识

### 4. 性能配置

```yaml
rate-limit:
  duplicate-submit:
    performance:
      # Key生成超时告警阈值（毫秒）
      key-generation-warn-threshold: 10
      
      # Redis操作超时告警阈值（毫秒）
      redis-operation-warn-threshold: 50
      
      # 是否启用Key生成缓存
      enable-key-cache: false
      
      # Key缓存大小
      key-cache-size: 1000
      
      # Key缓存过期时间（秒）
      key-cache-expire-seconds: 60
```

**配置说明**：
- `key-generation-warn-threshold`: Key生成耗时超过此阈值时记录警告日志
- `redis-operation-warn-threshold`: Redis操作耗时超过此阈值时记录警告日志
- `enable-key-cache`: 是否启用Key生成缓存（实验性功能）
- `key-cache-size`: Key缓存的最大大小
- `key-cache-expire-seconds`: Key缓存的过期时间

## 场景化配置

### 1. 高性能场景

```yaml
rate-limit:
  duplicate-submit:
    enabled: true
    lock-value-strategy: THREAD_TIME
    param:
      cache-hash: true
      max-serialized-length: 512
      ignore-null-values: true
    performance:
      key-generation-warn-threshold: 5
      redis-operation-warn-threshold: 20
      enable-key-cache: true
      key-cache-size: 2000
```

### 2. 高安全场景

```yaml
rate-limit:
  duplicate-submit:
    enabled: true
    lock-value-strategy: UUID
    enable-detail-log: true
    param:
      max-depth: 5
      cache-hash: false
      ignore-null-values: false
    performance:
      key-generation-warn-threshold: 20
      redis-operation-warn-threshold: 100
```

### 3. 开发调试场景

```yaml
rate-limit:
  duplicate-submit:
    enabled: true
    default-interval: 2
    enable-detail-log: true
    enable-metrics: true
    param:
      max-depth: 10
    performance:
      key-generation-warn-threshold: 1
      redis-operation-warn-threshold: 10

logging:
  level:
    com.marry.starter.ratelimit: DEBUG
```

### 4. 生产环境场景

```yaml
rate-limit:
  duplicate-submit:
    enabled: true
    default-interval: 5
    enable-metrics: true
    enable-detail-log: false
    param:
      cache-hash: true
      max-serialized-length: 1024
    performance:
      key-generation-warn-threshold: 10
      redis-operation-warn-threshold: 50

logging:
  level:
    com.marry.starter.ratelimit: WARN
```

## 配置验证

### 1. 配置加载验证

```java
@Autowired
private DuplicateSubmitProperties properties;

@PostConstruct
public void validateConfig() {
    logger.info("防重复提交配置: {}", properties);
    logger.info("默认间隔: {}秒", properties.getDefaultInterval());
    logger.info("Key前缀: {}", properties.getKeyPrefix());
}
```

### 2. 功能测试

```java
@RestController
public class ConfigTestController {
    
    @PostMapping("/config-test")
    @PreventDuplicateSubmit
    public Result testConfig(@RequestBody Map<String, Object> request) {
        return Result.success("配置测试成功");
    }
}
```

## 配置最佳实践

### 1. 环境隔离

```yaml
# application-dev.yml
rate-limit:
  duplicate-submit:
    default-interval: 2
    enable-detail-log: true

# application-prod.yml  
rate-limit:
  duplicate-submit:
    default-interval: 5
    enable-detail-log: false
```

### 2. 配置外部化

```yaml
# 使用环境变量
rate-limit:
  duplicate-submit:
    enabled: ${DUPLICATE_SUBMIT_ENABLED:true}
    default-interval: ${DUPLICATE_SUBMIT_INTERVAL:5}
    key-prefix: ${DUPLICATE_SUBMIT_PREFIX:duplicate_submit}
```

### 3. 配置监控

```yaml
# 启用配置监控
management:
  endpoints:
    web:
      exposure:
        include: configprops
  endpoint:
    configprops:
      enabled: true
```

### 4. 配置文档化

```java
/**
 * 防重复提交配置
 * 
 * 配置前缀: rate-limit.duplicate-submit
 * 
 * 示例配置:
 * rate-limit:
 *   duplicate-submit:
 *     enabled: true
 *     default-interval: 5
 */
@ConfigurationProperties(prefix = "rate-limit.duplicate-submit")
public class DuplicateSubmitProperties {
    // ...
}
```

## 故障排查

### 1. 配置不生效

**检查项**：
1. 确认`rate-limit.duplicate-submit.enabled=true`
2. 确认Redis连接正常
3. 确认AOP依赖已添加
4. 检查日志中的配置加载信息

### 2. 性能问题

**检查项**：
1. 查看Key生成耗时日志
2. 检查Redis操作耗时
3. 调整`max-serialized-length`参数
4. 启用`cache-hash`选项

### 3. 功能异常

**检查项**：
1. 确认注解使用正确
2. 检查参数策略配置
3. 验证用户标识提取逻辑
4. 查看详细日志输出

## 总结

通过独立的配置类和丰富的配置选项，防重复提交功能现在可以：

1. ✅ **灵活配置**: 支持多种场景的个性化配置
2. ✅ **性能调优**: 提供性能相关的配置选项
3. ✅ **环境适配**: 支持不同环境的配置隔离
4. ✅ **监控友好**: 内置监控和告警配置
5. ✅ **易于维护**: 统一的配置前缀和清晰的配置结构

这种配置方式使得防重复提交功能更加专业化和易于管理。
