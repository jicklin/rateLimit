# 参数分组功能设计文档

## 功能概述

参数分组功能允许将方法参数按照业务逻辑分组，每个分组独立进行防重复提交校验。这样可以实现更细粒度的控制，避免不相关的参数变化影响防重复提交的判断。

## 核心设计

### 1. 分组注解

```java
@DuplicateSubmitParam(
    include = true,
    group = "order",        // 分组名称
    groupWeight = 10,       // 分组权重
    alias = "orderId"       // 参数别名
)
String orderNumber
```

### 2. 分组策略

```java
@PreventDuplicateSubmit(
    groupStrategy = GroupStrategy.ALL_GROUPS,  // 分组策略
    groups = {"order", "payment"},             // 指定分组
    orderByWeight = true                       // 按权重排序
)
```

### 3. 分组处理流程

```
1. 参数解析 → 2. 分组归类 → 3. 分组过滤 → 4. 分组排序 → 5. 逐个检查 → 6. 锁管理
```

## 技术实现

### 1. 分组信息封装

```java
public class ParamGroupInfo {
    private final String groupName;     // 分组名称
    private final int weight;           // 分组权重
    private final Map<String, Object> params; // 分组参数
    
    public boolean isDefaultGroup() {
        return groupName == null || groupName.isEmpty();
    }
}
```

### 2. 分组生成逻辑

```java
private List<ParamGroupInfo> generateParamGroups(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
    Map<String, ParamGroupInfo> groupMap = new HashMap<>();
    
    // 遍历方法参数
    for (int i = 0; i < parameters.length; i++) {
        // 获取参数注解信息
        ParameterAnnotationInfo annotationInfo = getParameterAnnotationInfo(signature, i);
        
        if (shouldIncludeParameterWithInfo(annotationInfo, annotation)) {
            // 获取分组信息
            String groupName = getParameterGroupName(annotationInfo);
            int groupWeight = getParameterGroupWeight(annotationInfo);
            
            // 获取或创建分组
            ParamGroupInfo group = groupMap.computeIfAbsent(groupName, 
                name -> new ParamGroupInfo(name, groupWeight));
            
            // 添加参数到分组
            group.addParam(paramName, paramValue);
        }
    }
    
    return new ArrayList<>(groupMap.values());
}
```

### 3. 分组锁获取

```java
private String tryAcquireGroupLocks(List<ParamGroupInfo> groups, ...) {
    List<GroupLockInfo> acquiredLocks = new ArrayList<>();
    
    try {
        // 逐个检查每个分组
        for (ParamGroupInfo group : sortedGroups) {
            String groupKey = generateGroupKey(group, ...);
            String groupLockValue = tryAcquireSingleLock(groupKey, annotation);
            
            if (groupLockValue == null) {
                // 某个分组检测到重复提交，回滚已获取的锁
                rollbackAcquiredLocks(acquiredLocks);
                return null;
            }
            
            // 记录成功获取的锁
            acquiredLocks.add(new GroupLockInfo(group.getGroupName(), groupKey, groupLockValue));
        }
        
        // 组合锁值：groupName:groupKey:lockValue|groupName:groupKey:lockValue
        return combineLockValues(acquiredLocks);
        
    } catch (Exception e) {
        rollbackAcquiredLocks(acquiredLocks);
        return null;
    }
}
```

## 关键修复

### 1. 问题：groups.isEmpty()判断不准确

**问题描述**：
```java
// 原有逻辑
if (groups.isEmpty()) {
    // 使用传统方式
    return tryAcquireSingleLock(key, annotation);
}
```

当参数没有指定分组时，会被放入默认分组（空字符串），导致`groups.isEmpty()`返回false，但实际上应该使用传统方式。

**修复方案**：
```java
// 修复后的逻辑
boolean hasNonDefaultGroups = groups.stream()
    .anyMatch(group -> !group.isDefaultGroup());

if (groups.isEmpty() || (!hasNonDefaultGroups && annotation.groupStrategy() == GroupStrategy.ALL_GROUPS)) {
    // 使用传统方式
    return tryAcquireSingleLock(key, annotation);
}
```

### 2. 问题：分组锁失败时锁泄漏

**问题描述**：
```java
// 原有逻辑
for (ParamGroupInfo group : sortedGroups) {
    String groupLockValue = tryAcquireSingleLock(groupKey, annotation);
    
    if (groupLockValue == null) {
        // 直接返回null，之前获取的锁没有释放
        return null;
    }
}
```

**修复方案**：
```java
// 修复后的逻辑
List<GroupLockInfo> acquiredLocks = new ArrayList<>();

try {
    for (ParamGroupInfo group : sortedGroups) {
        String groupLockValue = tryAcquireSingleLock(groupKey, annotation);
        
        if (groupLockValue == null) {
            // 回滚已获取的锁
            rollbackAcquiredLocks(acquiredLocks);
            return null;
        }
        
        // 记录成功获取的锁
        acquiredLocks.add(new GroupLockInfo(...));
    }
} catch (Exception e) {
    rollbackAcquiredLocks(acquiredLocks);
    return null;
}
```

### 3. 问题：锁值格式不包含key信息

**问题描述**：
```java
// 原有格式：groupName:lockValue
lockValueBuilder.append(group.getGroupName()).append(":").append(groupLockValue);
```

释放锁时需要重新生成key，可能导致不一致。

**修复方案**：
```java
// 新格式：groupName:groupKey:lockValue
lockValueBuilder.append(lockInfo.getGroupName())
    .append(":").append(lockInfo.getGroupKey())
    .append(":").append(lockInfo.getLockValue());
```

## 使用示例

### 1. 基础分组

```java
@PostMapping("/order/create")
@PreventDuplicateSubmit(
    interval = 10,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result createOrder(
    @DuplicateSubmitParam(group = "order", groupWeight = 10) String orderNumber,
    @DuplicateSubmitParam(group = "order", groupWeight = 10) String orderType,
    @DuplicateSubmitParam(group = "user", groupWeight = 5) String userId,
    @DuplicateSubmitParam(group = "payment", groupWeight = 8) String paymentMethod) {
    
    // 分组检查顺序：order(权重10) -> payment(权重8) -> user(权重5)
    return processOrder(...);
}
```

### 2. 指定分组

```java
@PostMapping("/order/update")
@PreventDuplicateSubmit(
    interval = 5,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.SPECIFIED_GROUPS,
    groups = {"order", "payment"}  // 只检查这两个分组
)
public Result updateOrder(
    @DuplicateSubmitParam(group = "order") String orderNumber,
    @DuplicateSubmitParam(group = "user") String userId,      // 不会被检查
    @DuplicateSubmitParam(group = "payment") String paymentMethod,
    @DuplicateSubmitParam(group = "shipping") String address) { // 不会被检查
    
    // 只检查order和payment分组
    return updateOrder(...);
}
```

### 3. 排除分组

```java
@PostMapping("/order/query")
@PreventDuplicateSubmit(
    interval = 3,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.EXCEPT_GROUPS,
    groups = {"audit"}  // 排除audit分组
)
public Result queryOrder(
    @DuplicateSubmitParam(group = "order") String orderNumber,
    @DuplicateSubmitParam(group = "user") String userId,
    @DuplicateSubmitParam(group = "audit") String auditLog) { // 不会被检查
    
    // 检查除audit外的所有分组
    return queryOrder(...);
}
```

## 分组策略对比

| 策略 | 描述 | 使用场景 |
|------|------|----------|
| ALL_GROUPS | 检查所有分组 | 默认情况，需要全面检查 |
| SPECIFIED_GROUPS | 只检查指定分组 | 只关心特定业务逻辑 |
| EXCEPT_GROUPS | 排除指定分组 | 排除不重要的分组 |

## 性能考虑

### 1. 分组数量

- **建议**：每个方法的分组数量不超过5个
- **原因**：分组过多会增加检查开销

### 2. 分组权重

- **建议**：使用有意义的权重值（如10, 20, 30）
- **原因**：便于后续调整和维护

### 3. 锁回滚

- **开销**：分组锁失败时需要回滚已获取的锁
- **优化**：按权重排序，重要分组优先检查

## 最佳实践

### 1. 分组设计

```java
// ✅ 推荐：按业务逻辑分组
@DuplicateSubmitParam(group = "order", groupWeight = 10)    // 订单相关
@DuplicateSubmitParam(group = "payment", groupWeight = 8)   // 支付相关
@DuplicateSubmitParam(group = "user", groupWeight = 5)      // 用户相关

// ❌ 避免：按技术实现分组
@DuplicateSubmitParam(group = "string_params")
@DuplicateSubmitParam(group = "object_params")
```

### 2. 权重设置

```java
// ✅ 推荐：使用有层次的权重
@DuplicateSubmitParam(group = "critical", groupWeight = 100)   // 关键业务
@DuplicateSubmitParam(group = "important", groupWeight = 50)   // 重要业务
@DuplicateSubmitParam(group = "normal", groupWeight = 10)      // 普通业务

// ❌ 避免：权重值过于接近
@DuplicateSubmitParam(group = "group1", groupWeight = 10)
@DuplicateSubmitParam(group = "group2", groupWeight = 11)
```

### 3. 策略选择

```java
// ✅ 推荐：根据业务需求选择策略
@PreventDuplicateSubmit(
    groupStrategy = GroupStrategy.SPECIFIED_GROUPS,
    groups = {"order", "payment"}  // 只关心核心业务
)

// ✅ 推荐：使用权重排序
@PreventDuplicateSubmit(
    orderByWeight = true  // 重要分组优先检查
)
```

## 总结

参数分组功能提供了：

1. ✅ **细粒度控制**：每个分组独立校验
2. ✅ **灵活策略**：支持多种分组处理策略
3. ✅ **权重排序**：重要分组优先检查
4. ✅ **锁安全**：分组锁失败时自动回滚
5. ✅ **向后兼容**：不影响现有功能

这使得防重复提交功能能够适应更复杂的业务场景。
