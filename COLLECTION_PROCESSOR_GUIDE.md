# 集合处理器功能指南

## 功能概述

集合处理器允许参数值处理器返回集合类型的结果，集合中的每个元素将作为独立的组别进行防重复提交校验。这样可以实现对集合中单个元素的独立控制效果。

## 核心设计

### 1. 集合处理原理

```java
// 处理器返回集合
@DuplicateSubmitParam(processor = "split")
String productIds; // "PROD001,PROD002,PROD003"

// 处理结果
List<String> result = ["PROD001", "PROD002", "PROD003"];

// 生成的分组
products_0: {productIds[0]: "PROD001"}
products_1: {productIds[1]: "PROD002"}  
products_2: {productIds[2]: "PROD003"}
```

### 2. 独立控制效果

```java
// 每个产品ID独立校验
// 如果用户提交 "PROD001,PROD002"，只有这两个产品会被锁定
// 用户仍然可以提交 "PROD003,PROD004"，因为它们是不同的分组
```

## 内置集合处理器

### 1. 分割处理器 (split)

```java
@DuplicateSubmitParam(processor = "split", alias = "productIds")
String productList; // "PROD001,PROD002,PROD003"
```

**功能**: 按分隔符分割字符串成多个值
**分隔符规则**:
- 默认：逗号 `,`
- 标签/标记：逗号 `,`
- 路径/URL：斜杠 `/`
- ID列表：逗号 `,`

**处理示例**:
```java
// 输入
"PROD001,PROD002,PROD003"

// 输出
["PROD001", "PROD002", "PROD003"]

// 生成分组
products_0: {productIds[0]: "PROD001"}
products_1: {productIds[1]: "PROD002"}
products_2: {productIds[2]: "PROD003"}
```

## 自定义集合处理器

### 1. 基础实现

```java
@Component
public class CustomCollectionProcessor implements ParamValueProcessor {
    
    @Override
    public Object processValue(Object originalValue, String paramName, ProcessContext context) {
        if (originalValue == null) {
            return null;
        }
        
        // 处理字符串值
        if (originalValue instanceof String) {
            return processStringValue((String) originalValue, paramName, context);
        }
        
        // 处理Map值
        if (originalValue instanceof Map) {
            return processMapValue((Map<?, ?>) originalValue, paramName, context);
        }
        
        // 处理List值
        if (originalValue instanceof List) {
            return processListValue((List<?>) originalValue, paramName, context);
        }
        
        return originalValue;
    }
    
    @Override
    public String getName() {
        return "custom_collection";
    }
}
```

### 2. 字符串处理

```java
private Object processStringValue(String value, String paramName, ProcessContext context) {
    // 商品ID列表：PROD001,PROD002,PROD003
    if (paramName.toLowerCase().contains("product") && value.contains(",")) {
        List<String> productIds = new ArrayList<>();
        String[] parts = value.split(",");
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // 标准化商品ID：PROD001 -> PRODUCT_001
                if (trimmed.startsWith("PROD")) {
                    trimmed = "PRODUCT_" + trimmed.substring(4);
                }
                productIds.add(trimmed);
            }
        }
        
        return productIds.isEmpty() ? value : productIds;
    }
    
    // 用户角色列表：admin,user,guest
    if (paramName.toLowerCase().contains("role") && value.contains(",")) {
        List<String> roles = new ArrayList<>();
        String[] parts = value.split(",");
        
        for (String part : parts) {
            String trimmed = part.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                // 添加前缀：admin -> ROLE_ADMIN
                roles.add("ROLE_" + trimmed);
            }
        }
        
        return roles.isEmpty() ? value : roles;
    }
    
    return value;
}
```

### 3. Map处理

```java
private Object processMapValue(Map<?, ?> map, String paramName, ProcessContext context) {
    List<String> values = new ArrayList<>();
    
    // 提取categories
    if (map.containsKey("categories")) {
        Object categories = map.get("categories");
        if (categories instanceof List) {
            for (Object category : (List<?>) categories) {
                values.add("CATEGORY_" + category.toString().toUpperCase());
            }
        }
    }
    
    // 提取tags
    if (map.containsKey("tags")) {
        Object tags = map.get("tags");
        if (tags instanceof List) {
            for (Object tag : (List<?>) tags) {
                values.add("TAG_" + tag.toString().toLowerCase());
            }
        }
    }
    
    // 提取permissions
    if (map.containsKey("permissions")) {
        Object permissions = map.get("permissions");
        if (permissions instanceof List) {
            for (Object permission : (List<?>) permissions) {
                values.add("PERM_" + permission.toString().toUpperCase());
            }
        }
    }
    
    return values.isEmpty() ? map.toString() : values;
}
```

## 使用示例

### 1. 商品批量操作

```java
@PostMapping("/products/batch-update")
@PreventDuplicateSubmit(
    interval = 10,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result batchUpdateProducts(
    @DuplicateSubmitParam(
        group = "products", 
        processor = "split", 
        alias = "productIds"
    ) String productList,  // "PROD001,PROD002,PROD003"
    
    @DuplicateSubmitParam(
        group = "operation", 
        processor = "default", 
        alias = "action"
    ) String action) {
    
    // 每个产品ID会生成独立的分组：
    // products_0: {productIds[0]: "PROD001"}
    // products_1: {productIds[1]: "PROD002"}
    // products_2: {productIds[2]: "PROD003"}
    // operation: {action: "UPDATE"}
    
    return productService.batchUpdate(productList, action);
}
```

### 2. 用户权限管理

```java
@PostMapping("/user/assign-roles")
@PreventDuplicateSubmit(
    interval = 5,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result assignRoles(
    @DuplicateSubmitParam(
        group = "user", 
        processor = "default", 
        alias = "userId"
    ) String userId,
    
    @DuplicateSubmitParam(
        group = "roles", 
        processor = "custom_collection", 
        alias = "roleList"
    ) String roles) {  // "admin,user,guest"
    
    // 生成的分组：
    // user: {userId: "USER123"}
    // roles_0: {roleList[0]: "ROLE_ADMIN"}
    // roles_1: {roleList[1]: "ROLE_USER"}
    // roles_2: {roleList[2]: "ROLE_GUEST"}
    
    return userService.assignRoles(userId, roles);
}
```

### 3. 复杂对象处理

```java
@PostMapping("/content/tag-management")
@PreventDuplicateSubmit(
    interval = 8,
    paramStrategy = ParamStrategy.INCLUDE_ANNOTATED,
    groupStrategy = GroupStrategy.ALL_GROUPS
)
public Result manageContentTags(
    @DuplicateSubmitParam(
        group = "content", 
        processor = "default", 
        alias = "contentId"
    ) String contentId,
    
    @DuplicateSubmitParam(
        group = "metadata", 
        processor = "custom_collection", 
        path = "data", 
        alias = "metaInfo"
    ) @RequestBody Map<String, Object> request) {
    
    // request.data 包含：
    // {
    //   "categories": ["TECH", "MOBILE"],
    //   "tags": ["smartphone", "review"],
    //   "permissions": ["READ", "WRITE"]
    // }
    
    // 生成的分组：
    // content: {contentId: "CONTENT123"}
    // metadata_0: {metaInfo[0]: "CATEGORY_TECH"}
    // metadata_1: {metaInfo[1]: "CATEGORY_MOBILE"}
    // metadata_2: {metaInfo[2]: "TAG_smartphone"}
    // metadata_3: {metaInfo[3]: "TAG_review"}
    // metadata_4: {metaInfo[4]: "PERM_READ"}
    // metadata_5: {metaInfo[5]: "PERM_WRITE"}
    
    return contentService.manageTags(contentId, request);
}
```

## 分组生成规则

### 1. 集合参数分组

```java
// 原始分组名：products
// 集合元素：["PROD001", "PROD002", "PROD003"]

// 生成的分组：
products_0: {productIds[0]: "PROD001"}
products_1: {productIds[1]: "PROD002"}
products_2: {productIds[2]: "PROD003"}
```

### 2. 参数名生成

```java
// 原始参数名：productIds
// 集合索引：0, 1, 2

// 生成的参数名：
productIds[0], productIds[1], productIds[2]
```

### 3. 分组权重继承

```java
@DuplicateSubmitParam(
    group = "products", 
    groupWeight = 10,
    processor = "split"
)
String productList;

// 所有生成的分组都继承权重10：
// products_0 (权重10)
// products_1 (权重10)
// products_2 (权重10)
```

## 实际应用场景

### 1. 电商批量操作

```java
// 场景：用户批量加购物车
// 需求：每个商品独立控制，避免重复加购

@DuplicateSubmitParam(processor = "split")
String productIds; // "PROD001,PROD002,PROD003"

// 效果：
// - 用户提交 "PROD001,PROD002" 后，这两个商品被锁定
// - 用户仍可提交 "PROD003,PROD004"，因为是不同的商品
// - 但不能再次提交 "PROD001" 或 "PROD002"
```

### 2. 内容管理系统

```java
// 场景：批量标签管理
// 需求：每个标签独立控制，避免重复操作

@DuplicateSubmitParam(processor = "custom_collection")
String tags; // "tech,mobile,review"

// 效果：
// - 用户对 "tech,mobile" 标签操作后，这两个标签被锁定
// - 用户仍可操作 "review,news" 标签
// - 但不能再次操作 "tech" 或 "mobile" 标签
```

### 3. 权限管理系统

```java
// 场景：批量权限分配
// 需求：每个权限独立控制，避免重复分配

@DuplicateSubmitParam(processor = "custom_collection")
String permissions; // "READ,WRITE,DELETE"

// 效果：
// - 用户分配 "READ,WRITE" 权限后，这两个权限被锁定
// - 用户仍可分配 "DELETE,ADMIN" 权限
// - 但不能再次分配 "READ" 或 "WRITE" 权限
```

## 性能考虑

### 1. 集合大小限制

```java
// 建议限制集合大小，避免生成过多分组
private static final int MAX_COLLECTION_SIZE = 50;

@Override
public Object processValue(Object originalValue, String paramName, ProcessContext context) {
    List<String> result = processToList(originalValue);
    
    if (result.size() > MAX_COLLECTION_SIZE) {
        logger.warn("集合大小超过限制: size={}, limit={}", result.size(), MAX_COLLECTION_SIZE);
        // 截取或抛出异常
        return result.subList(0, MAX_COLLECTION_SIZE);
    }
    
    return result;
}
```

### 2. Redis键数量

```java
// 集合处理会增加Redis键的数量
// 原来：1个键
// 现在：N个键（N为集合大小）

// 监控Redis键数量
private void monitorRedisKeys(List<String> generatedKeys) {
    if (generatedKeys.size() > 20) {
        logger.warn("生成的Redis键数量较多: count={}", generatedKeys.size());
    }
}
```

### 3. 锁回滚开销

```java
// 集合处理时，如果某个分组失败，需要回滚更多的锁
// 建议按重要性排序，重要的分组优先检查

@DuplicateSubmitParam(
    group = "critical_products",
    groupWeight = 100,  // 高权重，优先检查
    processor = "split"
)
String criticalProducts;
```

## 最佳实践

### 1. 合理使用集合处理

```java
// ✅ 推荐：业务相关的集合
@DuplicateSubmitParam(processor = "split")
String productIds;  // 商品ID列表

@DuplicateSubmitParam(processor = "custom_collection")
String userRoles;   // 用户角色列表

// ❌ 避免：无关的数据集合
@DuplicateSubmitParam(processor = "split")
String randomData;  // 随机数据，无业务意义
```

### 2. 控制集合大小

```java
// ✅ 推荐：限制集合大小
private List<String> processWithLimit(String input, int maxSize) {
    List<String> result = Arrays.asList(input.split(","));
    return result.size() > maxSize ? result.subList(0, maxSize) : result;
}

// ✅ 推荐：验证输入
if (productIds.split(",").length > 20) {
    throw new IllegalArgumentException("商品数量不能超过20个");
}
```

### 3. 监控和日志

```java
// ✅ 推荐：记录集合处理信息
logger.info("集合处理: param={}, originalSize={}, processedSize={}", 
    paramName, originalSize, processedSize);

// ✅ 推荐：监控分组数量
if (generatedGroups.size() > 10) {
    logger.warn("生成分组数量较多: groups={}", generatedGroups.size());
}
```

## 总结

集合处理器功能提供了：

1. ✅ **独立控制**: 集合中每个元素独立进行防重复校验
2. ✅ **灵活处理**: 支持字符串、Map、List等多种输入类型
3. ✅ **自动分组**: 自动为集合元素生成独立的分组
4. ✅ **业务适配**: 适用于批量操作、权限管理等场景
5. ✅ **性能考虑**: 提供集合大小限制和监控机制

这使得防重复提交功能能够处理更复杂的业务场景，实现细粒度的控制效果。
