# Redis SETNX实现防重复提交

## 实现概述

使用Redis的`SET key value NX PX milliseconds`命令实现原子性的防重复提交检查，这是一个更安全、更高效的实现方式。

## 核心优势

### 1. 原子性操作

```lua
-- 原子性地设置key和过期时间
local result = redis.call('SET', key, value, 'NX', 'PX', ttl)
if result then
    -- 设置成功，不是重复提交
    return 0
else
    -- key已存在，是重复提交
    local remaining = redis.call('PTTL', key)
    return remaining > 0 and remaining or 1
end
```

### 2. 避免竞态条件

```java
// ❌ 传统方式：可能存在竞态条件
if (!redis.exists(key)) {
    redis.set(key, value);
    redis.expire(key, ttl);  // 如果这里失败，key永不过期
}

// ✅ SETNX方式：原子性操作
redis.eval(LUA_SCRIPT_CHECK_AND_SET, key, value, ttl);
```

### 3. 更高的性能

```java
// ❌ 传统方式：多次网络往返
// 1. EXISTS key
// 2. SET key value
// 3. PEXPIRE key ttl

// ✅ SETNX方式：单次网络往返
// 1. 执行Lua脚本（包含所有逻辑）
```

## Lua脚本实现

### 1. 完整脚本

```lua
local key = KEYS[1]
local value = ARGV[1]
local ttl = tonumber(ARGV[2])

-- 使用SET NX PX原子性地设置key和过期时间
local result = redis.call('SET', key, value, 'NX', 'PX', ttl)
if result then
    -- 设置成功，不是重复提交
    return 0
else
    -- key已存在，是重复提交，返回剩余TTL
    local remaining = redis.call('PTTL', key)
    return remaining > 0 and remaining or 1
end
```

### 2. 脚本解析

```lua
-- 参数说明
KEYS[1]  -- Redis key
ARGV[1]  -- 锁值（用于标识和验证）
ARGV[2]  -- TTL（毫秒）

-- 返回值说明
0        -- 设置成功，允许请求
>0       -- key已存在，返回剩余TTL（毫秒）
```

### 3. SET命令参数

```redis
SET key value NX PX milliseconds

NX  -- 仅当key不存在时设置
PX  -- 设置过期时间（毫秒）
```

## 实现对比

### 1. 传统EXISTS + SET方式

```java
// ❌ 问题：非原子性，存在竞态条件
public String tryAcquireLock(String key, String value, long ttl) {
    if (!redisTemplate.hasKey(key)) {
        redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.MILLISECONDS);
        return value;
    }
    return null;  // 重复提交
}
```

**问题分析**：
- 步骤1：检查key是否存在
- 步骤2：设置key和值
- 步骤3：设置过期时间

在高并发情况下，多个请求可能同时通过步骤1的检查，导致重复设置。

### 2. SETNX + PEXPIRE方式

```java
// ⚠️ 改进：使用SETNX，但仍有问题
public String tryAcquireLock(String key, String value, long ttl) {
    Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value);
    if (success) {
        redisTemplate.expire(key, ttl, TimeUnit.MILLISECONDS);
        return value;
    }
    return null;
}
```

**问题分析**：
- SETNX成功，但PEXPIRE失败 → key永不过期
- 两个命令之间存在时间窗口

### 3. SET NX PX方式（推荐）

```java
// ✅ 最佳实践：原子性操作
public String tryAcquireLock(String key, String value, long ttl) {
    String result = redisTemplate.execute(script, 
        Collections.singletonList(key), value, String.valueOf(ttl));
    return "0".equals(result) ? value : null;
}
```

**优势**：
- 单个原子操作
- 避免竞态条件
- 性能最优

## 并发场景分析

### 1. 高并发请求

```java
// 场景：1000个并发请求同时到达
// 请求1: SET key value1 NX PX 5000 → OK（成功）
// 请求2: SET key value2 NX PX 5000 → nil（失败）
// 请求3: SET key value3 NX PX 5000 → nil（失败）
// ...
// 请求1000: SET key value1000 NX PX 5000 → nil（失败）

// 结果：只有第一个请求成功，其他999个请求被正确拒绝
```

### 2. 时序图

```
时间轴: ----1----2----3----4----5----
请求A:  [SET NX] → OK
请求B:      [SET NX] → nil
请求C:          [SET NX] → nil
请求D:              [SET NX] → nil
```

### 3. 边界情况处理

```lua
-- 处理PTTL返回值的边界情况
local remaining = redis.call('PTTL', key)
return remaining > 0 and remaining or 1

-- PTTL返回值说明：
-- > 0: 正常的剩余TTL
-- -1: key存在但没有过期时间
-- -2: key不存在
```

## 错误处理

### 1. Redis连接异常

```java
try {
    Long result = redisTemplate.execute(script, 
        Collections.singletonList(key), value, String.valueOf(ttl));
    return result != null && result == 0L ? value : null;
} catch (Exception e) {
    logger.error("Redis操作异常: key={}", key, e);
    // 根据业务需求决定：
    // 1. 抛出异常，阻止请求
    // 2. 返回null，允许请求（降级策略）
    throw new DuplicateSubmitException("防重复检查失败", e);
}
```

### 2. Lua脚本执行异常

```java
// 脚本预编译和缓存
private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();
static {
    SCRIPT.setScriptText(LUA_SCRIPT_CHECK_AND_SET);
    SCRIPT.setResultType(Long.class);
}

// 执行时的异常处理
try {
    Long result = redisTemplate.execute(SCRIPT, 
        Collections.singletonList(key), value, String.valueOf(ttl));
    return result != null && result == 0L;
} catch (RedisScriptException e) {
    logger.error("Lua脚本执行异常: {}", e.getMessage(), e);
    throw new DuplicateSubmitException("防重复检查脚本执行失败", e);
}
```

### 3. 超时处理

```java
// 设置Redis操作超时
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    
    // 设置超时时间
    if (factory instanceof LettuceConnectionFactory) {
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
        lettuceFactory.setTimeout(Duration.ofMillis(1000));  // 1秒超时
    }
    
    return template;
}
```

## 性能优化

### 1. 脚本预编译

```java
// 预编译脚本，避免每次传输脚本内容
private static final String SCRIPT_SHA = "...";  // 脚本的SHA1值

public String tryAcquireLock(String key, String value, long ttl) {
    try {
        // 使用EVALSHA执行预编译的脚本
        Long result = redisTemplate.execute((RedisCallback<Long>) connection -> {
            return connection.evalSha(SCRIPT_SHA, ReturnType.INTEGER, 1, 
                key.getBytes(), value.getBytes(), String.valueOf(ttl).getBytes());
        });
        return result != null && result == 0L ? value : null;
    } catch (Exception e) {
        // 如果脚本不存在，回退到EVAL
        return tryAcquireLockWithEval(key, value, ttl);
    }
}
```

### 2. 连接池优化

```yaml
# application.yml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20      # 最大连接数
        max-idle: 10        # 最大空闲连接数
        min-idle: 5         # 最小空闲连接数
        max-wait: 1000ms    # 最大等待时间
    timeout: 1000ms         # 连接超时时间
```

### 3. 批量操作优化

```java
// 对于分组锁，可以使用Pipeline批量执行
public Map<String, String> tryAcquireMultipleLocks(Map<String, LockInfo> locks) {
    return redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        for (Map.Entry<String, LockInfo> entry : locks.entrySet()) {
            String key = entry.getKey();
            LockInfo lockInfo = entry.getValue();
            connection.eval(SCRIPT_BYTES, ReturnType.INTEGER, 1,
                key.getBytes(), lockInfo.getValue().getBytes(), 
                String.valueOf(lockInfo.getTtl()).getBytes());
        }
        return null;
    });
}
```

## 监控和指标

### 1. 关键指标

```java
// 防重复提交相关指标
@Component
public class DuplicateSubmitMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 请求总数
    private final Counter totalRequests;
    
    // 重复提交数
    private final Counter duplicateSubmits;
    
    // Redis操作耗时
    private final Timer redisOperationTimer;
    
    public void recordRequest(boolean isDuplicate, long duration) {
        totalRequests.increment();
        if (isDuplicate) {
            duplicateSubmits.increment();
        }
        redisOperationTimer.record(duration, TimeUnit.MILLISECONDS);
    }
}
```

### 2. 日志记录

```java
// 详细的操作日志
logger.debug("防重复检查: key={}, result={}, duration={}ms", 
    key, result, duration);

// 重复提交告警
if (result > 0) {
    logger.warn("检测到重复提交: key={}, remainingTtl={}ms, userAgent={}", 
        key, result, request.getHeader("User-Agent"));
}
```

### 3. 健康检查

```java
@Component
public class RedisHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // 执行简单的Redis操作检查连通性
            String result = redisTemplate.execute((RedisCallback<String>) connection -> {
                return connection.ping();
            });
            
            if ("PONG".equals(result)) {
                return Health.up()
                    .withDetail("redis", "连接正常")
                    .build();
            } else {
                return Health.down()
                    .withDetail("redis", "连接异常")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("redis", "连接失败: " + e.getMessage())
                .build();
        }
    }
}
```

## 最佳实践

### 1. 锁值设计

```java
// ✅ 推荐：包含时间戳和随机数
String lockValue = System.currentTimeMillis() + ":" + UUID.randomUUID().toString();

// ✅ 推荐：包含线程信息
String lockValue = Thread.currentThread().getId() + ":" + System.nanoTime();

// ❌ 避免：使用固定值
String lockValue = "LOCK";
```

### 2. TTL设置

```java
// ✅ 推荐：根据业务场景设置合理的TTL
int ttl = annotation.interval() * 1000;  // 转换为毫秒

// ✅ 推荐：设置最大TTL限制
int maxTtl = 300000;  // 5分钟
ttl = Math.min(ttl, maxTtl);

// ✅ 推荐：设置最小TTL限制
int minTtl = 1000;    // 1秒
ttl = Math.max(ttl, minTtl);
```

### 3. 异常处理策略

```java
// 根据业务重要性选择策略
public enum FailureStrategy {
    FAIL_FAST,      // 快速失败，抛出异常
    ALLOW_REQUEST,  // 允许请求通过（降级）
    RETRY_ONCE      // 重试一次
}
```

## 总结

Redis SETNX实现提供了：

1. ✅ **原子性**: SET NX PX在单个操作中完成检查和设置
2. ✅ **高性能**: 减少网络往返，提高响应速度
3. ✅ **高可靠**: 避免竞态条件，确保数据一致性
4. ✅ **易维护**: Lua脚本逻辑清晰，易于理解和维护
5. ✅ **可监控**: 提供丰富的指标和日志记录

这是实现分布式防重复提交的最佳实践方案。
