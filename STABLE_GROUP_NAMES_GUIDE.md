# 稳定分组名称功能指南

## 问题背景

在集合处理器功能中，当处理器返回集合时，原来的实现基于元素索引生成分组名称（如`products_0`, `products_1`, `products_2`）。这会导致一个严重问题：

### 问题示例

```java
// 第一次提交
String productList = "PROD001,PROD002,PROD003";
// 生成分组：products_0, products_1, products_2

// 第二次提交（顺序变化）
String productList = "PROD003,PROD001,PROD002";
// 生成分组：products_0, products_1, products_2
// 但内容不同！PROD003现在在products_0位置
```

**结果**：相同的产品组合因为顺序不同而被认为是不同的请求，防重复校验失效。

## 解决方案

### 1. 基于内容的稳定分组名称

```java
// 不再基于索引，而是基于元素内容
// PROD001 → products_PROD001_a1b2c3d4
// PROD002 → products_PROD002_e5f6g7h8
// PROD003 → products_PROD003_i9j0k1l2

// 无论顺序如何，相同内容总是生成相同的分组名
```

### 2. 分组名称生成规则

```java
public static String generateElementGroupName(String baseGroupName, Object element, int elementIndex) {
    // 1. 提取安全字符
    String safeContent = generateSafeContent(elementStr);
    
    // 2. 生成内容哈希
    String contentHash = generateContentHash(elementStr);
    
    // 3. 组合生成稳定名称
    return baseGroupName + "_" + safeContent + "_" + contentHash;
}
```

### 3. 安全字符提取

```java
private static String generateSafeContent(String content) {
    StringBuilder safeContent = new StringBuilder();
    
    // 只保留字母、数字、下划线、连字符
    for (char c : content.toCharArray()) {
        if (SAFE_CHARS.matcher(String.valueOf(c)).matches()) {
            safeContent.append(c);
            if (safeContent.length() >= MAX_CONTENT_LENGTH) {
                break;
            }
        }
    }
    
    // 如果没有安全字符，使用长度标识
    if (safeContent.length() == 0) {
        safeContent.append("len").append(content.length());
    }
    
    return safeContent.toString();
}
```

### 4. 内容哈希生成

```java
private static String generateContentHash(String content) {
    String md5Hash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    return md5Hash.substring(0, Math.min(HASH_LENGTH, md5Hash.length()));
}
```

## 实现细节

### 1. ParamGroupInfo增强

```java
public class ParamGroupInfo {
    private final String groupName;        // 基础名称：products
    private String actualGroupName;        // 实际名称：products_PROD001_a1b2c3d4
    private final int weight;
    private final Map<String, Object> params;
    
    // 构造函数支持设置实际分组名称
    public ParamGroupInfo(String groupName, String actualGroupName, int weight) {
        this.groupName = groupName;
        this.actualGroupName = actualGroupName;
        this.weight = weight;
        this.params = new TreeMap<>();
    }
}
```

### 2. 集合处理逻辑修改

```java
// 修改前：基于索引
for (int j = 0; j < valueList.size(); j++) {
    String indexedGroupName = groupName + "_" + j;  // 不稳定
    // ...
}

// 修改后：基于内容
for (int j = 0; j < valueList.size(); j++) {
    Object element = valueList.get(j);
    String actualGroupName = GroupNameGenerator.generateElementGroupName(groupName, element, j);  // 稳定
    
    ParamGroupInfo elementGroup = groupMap.computeIfAbsent(actualGroupName, 
        name -> new ParamGroupInfo(groupName, actualGroupName, groupWeight));
    // ...
}
```

### 3. 锁值生成使用实际分组名称

```java
// 使用实际分组名称记录锁信息
acquiredLocks.add(new GroupLockInfo(group.getActualGroupName(), groupKey, groupLockValue));

// 锁值格式：actualGroupName:groupKey:lockValue
"products_PROD001_a1b2c3d4:duplicate_submit:...:lockValue"
```

## 使用示例

### 1. 商品列表处理

```java
@PostMapping("/products/batch-process")
@PreventDuplicateSubmit(
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result batchProcessProducts(
    @DuplicateSubmitParam(group = "products", processor = "split") String productList) {
    
    // 输入1：PROD001,PROD002,PROD003
    // 生成分组：
    // - products_PROD001_a1b2c3d4
    // - products_PROD002_e5f6g7h8  
    // - products_PROD003_i9j0k1l2
    
    // 输入2：PROD003,PROD001,PROD002（顺序变化）
    // 生成分组：
    // - products_PROD001_a1b2c3d4  ← 相同
    // - products_PROD002_e5f6g7h8  ← 相同
    // - products_PROD003_i9j0k1l2  ← 相同
    
    // 结果：防重复校验正常工作
    return productService.batchProcess(productList);
}
```

### 2. 订单批量操作

```java
@PostMapping("/orders/batch-update")
@PreventDuplicateSubmit(
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result batchUpdateOrders(
    @DuplicateSubmitParam(group = "orders", processor = "split") String orderList) {
    
    // 无论用户如何排列订单ID，相同的订单组合总是生成相同的分组名称
    return orderService.batchUpdate(orderList);
}
```

### 3. 复杂对象处理

```java
@PostMapping("/content/batch-tag")
@PreventDuplicateSubmit(
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result batchTagContent(
    @DuplicateSubmitParam(group = "tags", processor = "custom_collection") 
    @RequestBody Map<String, Object> request) {
    
    // request包含tags数组，每个标签生成稳定的分组名称
    // tags_tech_a1b2c3d4, tags_mobile_e5f6g7h8
    return contentService.batchTag(request);
}
```

## 分组名称格式

### 1. 标准格式

```
baseGroupName_safeContent_contentHash
```

### 2. 具体示例

| 原始内容 | 安全内容 | 内容哈希 | 分组名称 |
|----------|----------|----------|----------|
| `PROD001` | `PROD001` | `a1b2c3d4` | `products_PROD001_a1b2c3d4` |
| `ORDER-123` | `ORDER123` | `e5f6g7h8` | `orders_ORDER123_e5f6g7h8` |
| `用户@123` | `123` | `i9j0k1l2` | `users_123_i9j0k1l2` |
| `""` | `empty` | `d41d8cd9` | `items_empty_d41d8cd9` |
| `null` | `null` | `37a6259c` | `items_null_37a6259c` |

### 3. 特殊情况处理

```java
// 空内容
if (contentStr.isEmpty()) {
    return baseGroupName + "_empty";
}

// null值
if (content == null) {
    return baseGroupName + "_null";
}

// 无安全字符
if (safeContent.length() == 0) {
    safeContent.append("len").append(content.length());
}
```

## 性能考虑

### 1. 哈希计算开销

```java
// MD5哈希计算相对较快
// 对于短字符串（<100字符），性能影响可忽略
String md5Hash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
```

### 2. 分组名称长度

```java
// 控制分组名称长度，避免过长
private static final int MAX_CONTENT_LENGTH = 20;
private static final int HASH_LENGTH = 8;

// 最大长度约为：baseGroupName(20) + safeContent(20) + hash(8) + 分隔符(2) = 50字符
```

### 3. 内存使用

```java
// 每个分组额外存储实际分组名称
// 内存增加：每个分组约50字节
// 对于大多数场景，影响可忽略
```

## 兼容性

### 1. 向后兼容

```java
// 旧版本的分组名称仍然有效
// 新版本能正确处理旧格式的锁值
if (parts.length == 2) {
    // 兼容旧格式：groupName:lockValue
    String groupName = parts[0];
    String groupLockValue = parts[1];
    // ...
}
```

### 2. 渐进升级

```java
// 系统升级时，新旧格式可以共存
// 旧的锁会自然过期，新的锁使用新格式
```

## 测试验证

### 1. 顺序无关性测试

```java
@Test
public void testOrderIndependence() {
    // 测试相同内容不同顺序生成相同分组名称
    String[] input1 = {"PROD001", "PROD002", "PROD003"};
    String[] input2 = {"PROD003", "PROD001", "PROD002"};
    
    Set<String> groups1 = generateGroupNames("products", input1);
    Set<String> groups2 = generateGroupNames("products", input2);
    
    assertEquals(groups1, groups2);  // 应该相等
}
```

### 2. 唯一性测试

```java
@Test
public void testUniqueness() {
    // 测试不同内容生成不同分组名称
    String[] inputs = {"PROD001", "PROD002", "PROD001A"};
    
    Set<String> groupNames = generateGroupNames("products", inputs);
    
    assertEquals(3, groupNames.size());  // 应该有3个不同的分组名称
}
```

### 3. 稳定性测试

```java
@Test
public void testStability() {
    // 测试多次生成相同内容的分组名称
    String content = "PROD001";
    
    String name1 = GroupNameGenerator.generateElementGroupName("products", content, 0);
    String name2 = GroupNameGenerator.generateElementGroupName("products", content, 1);
    
    assertEquals(name1, name2);  // 应该相等（索引不影响结果）
}
```

## 最佳实践

### 1. 内容设计

```java
// ✅ 推荐：使用有意义的标识符
"PROD001", "ORDER123", "USER456"

// ❌ 避免：使用随机或时间相关的内容
UUID.randomUUID().toString(), System.currentTimeMillis()
```

### 2. 集合大小控制

```java
// ✅ 推荐：限制集合大小
if (valueList.size() > MAX_COLLECTION_SIZE) {
    throw new IllegalArgumentException("集合大小超过限制");
}
```

### 3. 监控和日志

```java
// ✅ 推荐：记录分组名称生成信息
logger.debug("生成稳定分组名称: base={}, content={}, actual={}", 
    baseGroupName, content, actualGroupName);
```

## 总结

稳定分组名称功能解决了集合处理中的关键问题：

1. ✅ **顺序无关**: 相同内容不同顺序生成相同分组名称
2. ✅ **内容唯一**: 不同内容生成不同分组名称  
3. ✅ **稳定可靠**: 多次生成相同内容总是得到相同结果
4. ✅ **性能友好**: 哈希计算开销小，分组名称长度可控
5. ✅ **向后兼容**: 不影响现有功能，支持渐进升级

这确保了集合处理器在各种场景下都能正确工作，提供可靠的防重复提交保护。
