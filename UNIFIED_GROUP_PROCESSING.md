# 统一分组处理架构

## 修改概述

将`tryAcquireLock`和`releaseLock`方法统一使用分组处理方式，无论是传统单个锁还是分组锁，都通过`tryAcquireGroupLocks`和`releaseGroupLocks`方法处理。这样简化了代码逻辑，提高了一致性。

## 核心设计

### 1. 统一入口

```java
// 修改前：分别处理
public String tryAcquireLock(...) {
    if (groups.isEmpty() || isTraditional) {
        return tryAcquireSingleLock(key, annotation);  // 传统方式
    } else {
        return tryAcquireGroupLocks(groups, ...);      // 分组方式
    }
}

// 修改后：统一处理
public String tryAcquireLock(...) {
    List<ParamGroupInfo> groups = generateParamGroups(...);
    
    // 如果是传统方式，创建默认分组
    if (isTraditionalCase) {
        ParamGroupInfo defaultGroup = new ParamGroupInfo("default", 0);
        String key = generateKey(...);
        defaultGroup.addParam("traditional_key", key);
        groups = Collections.singletonList(defaultGroup);
    }
    
    // 统一使用分组方式处理
    return tryAcquireGroupLocks(groups, ...);
}
```

### 2. 默认分组处理

```java
// 传统方式转换为默认分组
ParamGroupInfo defaultGroup = new ParamGroupInfo("default", 0);
String traditionalKey = generateKey(joinPoint, request, annotation);
defaultGroup.addParam("traditional_key", traditionalKey);

// 这样传统方式也能通过分组流程处理
```

### 3. 分组锁获取统一处理

```java
// 在 tryAcquireGroupLocks 中
for (ParamGroupInfo group : sortedGroups) {
    String groupKey;
    
    // 检查是否为默认分组（传统方式）
    if ("default".equals(group.getGroupName()) && group.getParams().containsKey("traditional_key")) {
        // 使用传统key
        groupKey = (String) group.getParams().get("traditional_key");
    } else {
        // 使用分组key
        groupKey = generateGroupKey(group, joinPoint, request, annotation);
    }
    
    String groupLockValue = tryAcquireSingleLock(groupKey, annotation);
    // ... 处理逻辑
}
```

## 锁值格式

### 1. 传统锁值

```java
// 传统方式生成的锁值格式
"default:traditional_key_value:lock_value"

// 示例
"default:duplicate_submit:com.example.Controller.method:user:123:params:hash:1234567890:987654321"
```

### 2. 分组锁值

```java
// 单个分组
"products:group_key_value:lock_value"

// 多个分组
"products:group_key1:lock_value1|users:group_key2:lock_value2"

// 集合分组
"products_0:group_key1:lock_value1|products_1:group_key2:lock_value2"
```

### 3. 锁值识别

```java
// 在 releaseGroupLocks 中
private boolean releaseGroupLocks(String combinedLockValue, ...) {
    // 检查是否为传统锁值（不包含冒号或分隔符）
    if (!combinedLockValue.contains(":") && !combinedLockValue.contains("|")) {
        // 传统单个锁值，直接使用传统key释放
        String key = generateKey(joinPoint, request, annotation);
        return releaseLockWithKey(key, combinedLockValue);
    }
    
    // 分组锁值处理
    String[] groupLocks = combinedLockValue.split("\\|");
    // ... 处理每个分组锁
}
```

## 处理流程

### 1. 锁获取流程

```
1. generateParamGroups() → 生成参数分组
2. 检查是否为传统情况 → 创建默认分组（如果需要）
3. tryAcquireGroupLocks() → 统一分组处理
4. 对每个分组调用 tryAcquireSingleLock()
5. 组合锁值返回
```

### 2. 锁释放流程

```
1. releaseLock() → 统一入口
2. releaseGroupLocks() → 统一分组处理
3. 检查锁值格式 → 传统锁值 or 分组锁值
4. 对每个锁调用 releaseLockWithKey()
5. 返回释放结果
```

## 代码简化

### 1. 移除重复逻辑

```java
// 修改前：两套处理逻辑
- tryAcquireSingleLock() 用于传统方式
- tryAcquireGroupLocks() 用于分组方式
- 两套不同的锁值格式和释放逻辑

// 修改后：统一处理逻辑
- 只有 tryAcquireGroupLocks() 
- 统一的锁值格式
- 统一的释放逻辑
```

### 2. 减少判断分支

```java
// 修改前：多个判断分支
if (groups.isEmpty()) {
    // 传统方式
} else if (hasNonDefaultGroups) {
    // 分组方式
} else {
    // 其他情况
}

// 修改后：统一流程
// 所有情况都转换为分组处理
return tryAcquireGroupLocks(groups, ...);
```

### 3. 统一错误处理

```java
// 修改前：两套错误处理
- 传统方式的异常处理
- 分组方式的异常处理和回滚

// 修改后：统一错误处理
- 只有分组方式的异常处理和回滚
- 传统方式也享受分组的回滚机制
```

## 兼容性保证

### 1. 传统锁值兼容

```java
// 老版本生成的传统锁值
"simple_lock_value_123456"

// 新版本仍能正确识别和释放
if (!combinedLockValue.contains(":") && !combinedLockValue.contains("|")) {
    // 识别为传统锁值，使用传统key释放
    String key = generateKey(...);
    return releaseLockWithKey(key, combinedLockValue);
}
```

### 2. 分组锁值兼容

```java
// 支持多种分组锁值格式
if (parts.length == 3) {
    // 新格式：groupName:groupKey:lockValue
} else if (parts.length == 2) {
    // 兼容旧格式：groupName:lockValue
}
```

### 3. API兼容

```java
// 对外API保持不变
public String tryAcquireLock(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation)
public boolean releaseLock(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation, String lockValue)

// 内部实现统一为分组处理
```

## 性能影响

### 1. 传统方式性能

```java
// 修改前：直接处理
tryAcquireSingleLock(key, annotation)

// 修改后：通过分组处理
ParamGroupInfo defaultGroup = new ParamGroupInfo("default", 0);
defaultGroup.addParam("traditional_key", key);
tryAcquireGroupLocks(Collections.singletonList(defaultGroup), ...)

// 性能影响：微小增加（创建分组对象的开销）
```

### 2. 分组方式性能

```java
// 修改前后：处理逻辑相同
// 性能影响：无变化
```

### 3. 内存使用

```java
// 传统方式额外创建：
- 1个 ParamGroupInfo 对象
- 1个 HashMap（存储traditional_key）
- 1个 ArrayList（存储单个分组）

// 内存影响：可忽略
```

## 测试验证

### 1. 传统方式测试

```java
@PostMapping("/traditional-test")
@PreventDuplicateSubmit(interval = 5)
public Result traditionalTest(String param1, String param2) {
    // 应该生成默认分组，使用传统key
    // 锁值格式：default:traditional_key:lock_value
    return Result.success("传统方式测试");
}
```

### 2. 分组方式测试

```java
@PostMapping("/group-test")
@PreventDuplicateSubmit(
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result groupTest(
    @DuplicateSubmitParam(group = "test") String param) {
    // 应该生成test分组
    // 锁值格式：test:group_key:lock_value
    return Result.success("分组方式测试");
}
```

### 3. 混合方式测试

```java
@PostMapping("/mixed-test")
@PreventDuplicateSubmit(
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result mixedTest(
    @DuplicateSubmitParam(group = "group1") String param1,
    @DuplicateSubmitParam(processor = "split") String param2) {
    // 应该生成多个分组
    // 锁值格式：group1:key1:value1|group2_0:key2:value2|group2_1:key3:value3
    return Result.success("混合方式测试");
}
```

## 日志记录

### 1. 统一日志格式

```java
// 传统方式日志
logger.debug("传统锁获取成功: key={}, lockValue={}", key, lockValue);
logger.debug("传统锁释放成功: key={}, lockValue={}", key, lockValue);

// 分组方式日志
logger.debug("分组锁获取成功: group={}, key={}, lockValue={}", groupName, key, lockValue);
logger.debug("分组锁释放成功: group={}, key={}, lockValue={}", groupName, key, lockValue);
```

### 2. 调试信息

```java
// 分组生成日志
logger.debug("生成参数分组: groups={}, isTraditional={}", groups.size(), isTraditional);

// 锁值组合日志
logger.debug("组合锁值: combinedLockValue={}, groupCount={}", combinedLockValue, groupCount);
```

## 优势总结

### 1. 代码简化

- ✅ 统一处理逻辑，减少代码重复
- ✅ 减少判断分支，提高可读性
- ✅ 统一错误处理和回滚机制

### 2. 功能增强

- ✅ 传统方式也享受分组的回滚机制
- ✅ 统一的锁值格式和处理逻辑
- ✅ 更好的扩展性和维护性

### 3. 兼容性

- ✅ 完全向后兼容
- ✅ 支持多种锁值格式
- ✅ API接口保持不变

### 4. 性能

- ✅ 分组方式性能无变化
- ✅ 传统方式性能影响微小
- ✅ 内存使用增加可忽略

这种统一处理的架构使得代码更加简洁、一致和易于维护，同时保持了完全的向后兼容性。
