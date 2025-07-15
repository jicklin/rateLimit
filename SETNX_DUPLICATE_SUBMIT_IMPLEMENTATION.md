# 基于Redis SETNX的防重复提交实现

## 实现原理

使用Redis的SETNX（SET if Not eXists）命令结合Lua脚本实现原子性的防重复提交检查和记录。

## 核心优势

### 1. 原子性操作
- **SETNX特性**: 只有当key不存在时才设置成功
- **Lua脚本**: 保证检查和设置操作的原子性
- **无竞态条件**: 避免并发请求的竞态问题

### 2. 性能优化
- **单次操作**: 检查和记录合并为一次Redis操作
- **减少网络开销**: 从2次Redis调用减少到1次
- **更高吞吐量**: 减少Redis连接占用时间

### 3. 一致性保证
- **强一致性**: 利用Redis单线程特性保证操作一致性
- **无数据竞争**: 避免检查和设置之间的时间窗口问题

## 技术实现

### 1. Lua脚本设计

#### 检查和设置脚本
```lua
local key = KEYS[1]
local value = ARGV[1]
local ttl = tonumber(ARGV[2])
local exists = redis.call('EXISTS', key)
if exists == 0 then
    redis.call('SET', key, value, 'PX', ttl)
    return 0
else
    local remaining = redis.call('PTTL', key)
    return remaining > 0 and remaining or 1
end
```

**返回值说明**：
- `0`: 设置成功，不是重复提交
- `>0`: key已存在，返回剩余TTL（毫秒）

#### 获取TTL脚本
```lua
local key = KEYS[1]
local ttl = redis.call('PTTL', key)
return ttl > 0 and ttl or 0
```

### 2. Java实现

```java
@Override
public boolean isDuplicateSubmit(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
    String key = generateKey(joinPoint, request, annotation);
    long interval = annotation.timeUnit().toMillis(annotation.interval());
    
    // 使用Lua脚本原子性地检查和设置
    Long result = redisTemplate.execute(checkAndSetScript, 
        Collections.singletonList(key), 
        String.valueOf(System.currentTimeMillis()), 
        String.valueOf(interval));
    
    boolean isDuplicate = result != null && result != 0;
    
    if (isDuplicate) {
        logger.debug("检测到重复提交: key={}, remainingTime={}ms", key, result);
    } else {
        logger.debug("首次提交，已记录: key={}, interval={}ms", key, interval);
    }
    
    return isDuplicate;
}
```

## 方案对比

### 传统方案（两步操作）
```java
// 步骤1：检查key是否存在
if (redisTemplate.hasKey(key)) {
    return true; // 重复提交
}

// 步骤2：设置key（可能存在竞态条件）
redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.MILLISECONDS);
return false;
```

**问题**：
- 两次Redis操作之间存在时间窗口
- 并发请求可能同时通过检查
- 可能导致重复提交检测失效

### SETNX方案（原子操作）
```java
// 单次原子操作：检查并设置
Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, ttl, TimeUnit.MILLISECONDS);
return !Boolean.TRUE.equals(success);
```

**优势**：
- 单次原子操作
- 无竞态条件
- 性能更好

### Lua脚本方案（最优）
```java
// 使用Lua脚本，同时返回剩余时间
Long result = redisTemplate.execute(luaScript, keys, args);
return result != 0;
```

**优势**：
- 原子性保证
- 同时获取剩余时间
- 减少网络往返

## 性能分析

### 1. Redis操作次数对比

| 方案 | 正常请求 | 重复请求 | 总操作数 |
|------|----------|----------|----------|
| 传统方案 | 2次 | 1次 | 平均1.5次 |
| SETNX方案 | 1次 | 1次 | 1次 |
| Lua脚本方案 | 1次 | 1次 | 1次 |

### 2. 网络延迟影响

```
传统方案延迟 = 2 × 网络RTT + 2 × Redis处理时间
SETNX方案延迟 = 1 × 网络RTT + 1 × Redis处理时间
```

**性能提升**: 约50%的延迟减少

### 3. 并发安全性

| 方案 | 并发安全 | 竞态条件 | 一致性 |
|------|----------|----------|--------|
| 传统方案 | ❌ | 存在 | 弱一致性 |
| SETNX方案 | ✅ | 无 | 强一致性 |
| Lua脚本方案 | ✅ | 无 | 强一致性 |

## 实际应用场景

### 1. 高并发场景
```java
@PostMapping("/flash-sale")
@PreventDuplicateSubmit(interval = 1, timeUnit = TimeUnit.SECONDS)
public Result flashSale(@RequestBody FlashSaleRequest request) {
    // 秒杀场景，1秒防重复
    return processFlashSale(request);
}
```

### 2. 支付场景
```java
@PostMapping("/payment")
@PreventDuplicateSubmit(interval = 30, timeUnit = TimeUnit.SECONDS)
public Result payment(@RequestBody PaymentRequest request) {
    // 支付场景，30秒防重复
    return processPayment(request);
}
```

### 3. 表单提交
```java
@PostMapping("/submit-form")
@PreventDuplicateSubmit(interval = 5, timeUnit = TimeUnit.SECONDS)
public Result submitForm(@RequestBody FormRequest request) {
    // 表单提交，5秒防重复
    return processForm(request);
}
```

## 错误处理

### 1. Redis异常处理
```java
try {
    Long result = redisTemplate.execute(checkAndSetScript, keys, args);
    return result != null && result != 0;
} catch (Exception e) {
    logger.error("执行防重复提交检查异常: key={}", key, e);
    // 异常时为了安全起见，认为不是重复提交，允许请求通过
    return false;
}
```

### 2. 降级策略
- **Redis不可用**: 允许请求通过，记录错误日志
- **脚本执行失败**: 降级到简单的SETNX操作
- **网络超时**: 设置合理的超时时间

## 监控指标

### 1. 关键指标
```java
// 防重复提交命中率
double hitRate = duplicateCount / totalCount;

// 平均响应时间
long avgResponseTime = totalResponseTime / requestCount;

// Redis操作成功率
double redisSuccessRate = successCount / totalRedisOps;
```

### 2. 告警规则
```yaml
# 重复提交率过高告警
duplicate_submit_rate > 10%

# Redis操作失败率告警  
redis_failure_rate > 1%

# 响应时间过长告警
avg_response_time > 100ms
```

## 配置优化

### 1. Redis连接池配置
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 2000ms
    timeout: 2000ms
```

### 2. Lua脚本缓存
```java
// Spring会自动缓存Lua脚本的SHA1值
// 避免每次发送完整脚本内容
private final DefaultRedisScript<Long> checkAndSetScript;
```

## 最佳实践

### 1. 时间间隔设置
```java
// 根据业务场景设置合适的时间间隔
@PreventDuplicateSubmit(interval = 5)    // 一般表单：5秒
@PreventDuplicateSubmit(interval = 30)   // 支付操作：30秒
@PreventDuplicateSubmit(interval = 1)    // 高频操作：1秒
```

### 2. 异常处理
```java
// 提供友好的错误信息
@ExceptionHandler(DuplicateSubmitException.class)
public ResponseEntity<Result> handleDuplicateSubmit(DuplicateSubmitException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Result.error(e.getMessage())
            .put("retryAfter", e.getRemainingTimeInSeconds()));
}
```

### 3. 日志记录
```java
// 记录关键操作日志
logger.info("防重复提交检查: key={}, result={}, user={}", 
    key, isDuplicate ? "重复" : "通过", userInfo);
```

## 总结

基于Redis SETNX和Lua脚本的防重复提交实现具有以下优势：

1. ✅ **原子性保证**: 避免竞态条件
2. ✅ **性能优秀**: 减少Redis操作次数
3. ✅ **一致性强**: 利用Redis单线程特性
4. ✅ **扩展性好**: 支持集群部署
5. ✅ **监控友好**: 提供详细的执行信息

这种实现方式在高并发场景下表现优异，是生产环境的最佳选择。
