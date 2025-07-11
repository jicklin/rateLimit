# 真正使用采样和热点数据

## 问题分析

您的观察非常准确！之前的`getDetailedStatsMap`方法虽然声称使用"采样和热点统计"，但实际上：

1. `getHotspotStats`方法还是基于`getDimensionStats`（详细统计）
2. `getSamplingInfo`方法只是包装了基础统计数据
3. 没有真正从Redis中读取采样和热点的优化数据

## 修复内容

### 1. 真正的热点统计

**修改前**：
```java
// 基于详细统计数据排序
List<DetailedRateLimitStats> dimensionStats = getDimensionStats(ruleId, dimension);
List<DetailedRateLimitStats> topStats = dimensionStats.stream()
    .sorted((a, b) -> Long.compare(b.getTotalRequests(), a.getTotalRequests()))
    .limit(topN)
    .collect(Collectors.toList());
```

**修改后**：
```java
// 直接从Redis ZSet获取热点数据
String hotspotKey = keyGenerator.getRedisKeyPrefix() + ":hotspot_stats:" + ruleId + ":" + dimension;
Set<Object> hotspotData = redisTemplate.opsForZSet().reverseRangeWithScores(hotspotKey, 0, topN - 1);

// 解析ZSet数据
for (Object item : hotspotData) {
    DefaultTypedTuple tuple = (DefaultTypedTuple) item;
    Map<String, Object> hotspot = new HashMap<>();
    hotspot.put("dimensionValue", tuple.getValue());
    hotspot.put("accessCount", tuple.getScore().longValue());
    hotspots.add(hotspot);
}
```

### 2. 真正的采样统计

**修改前**：
```java
// 只是包装基础统计
RateLimitStats basicStats = getStats(ruleId);
samplingInfo.put("totalRequests", basicStats.getTotalRequests());
samplingInfo.put("blockedRequests", basicStats.getBlockedRequests());
```

**修改后**：
```java
// 从Redis获取实际采样数据
String sampledKey = keyGenerator.getRedisKeyPrefix() + ":sampled_stats:" + ruleId + ":total:100";
Map<Object, Object> sampledData = redisTemplate.opsForHash().entries(sampledKey);

if (!sampledData.isEmpty()) {
    long totalSamples = getLongValue(sampledData.get("totalSamples"));
    long blockedSamples = getLongValue(sampledData.get("blockedSamples"));
    
    // 基于采样数据估算总体
    long estimatedTotal = totalSamples * 100; // 1%采样
    long estimatedBlocked = blockedSamples * 100;
    
    samplingInfo.put("actualSamples", totalSamples);
    samplingInfo.put("estimatedTotal", estimatedTotal);
    samplingInfo.put("dataSource", "实际采样数据");
}
```

### 3. 真正的聚合统计

**修改前**：
```java
// 使用基础统计的平均值
RateLimitStats stats = getStats(ruleId);
window.put("avgRequests", stats.getTotalRequests() / windowCount);
```

**修改后**：
```java
// 从Redis获取实际聚合数据
String aggKey = keyGenerator.getRedisKeyPrefix() + ":agg_stats:" + ruleId + ":total:" + alignedStart;
Map<Object, Object> aggData = redisTemplate.opsForHash().entries(aggKey);

if (!aggData.isEmpty()) {
    window.put("totalRequests", getLongValue(aggData.get("totalRequests")));
    window.put("allowedRequests", getLongValue(aggData.get("allowedRequests")));
    window.put("blockedRequests", getLongValue(aggData.get("blockedRequests")));
    window.put("dataSource", "实际聚合数据");
}
```

## 数据结构对比

### 热点统计数据

**修改前**（基于详细统计）：
```json
{
  "hotspots": [
    {
      "ruleId": "rule1",
      "dimension": "ip",
      "dimensionValue": "192.168.1.1",
      "totalRequests": 1000,
      "allowedRequests": 950,
      "blockedRequests": 50,
      "blockRate": 5.0
    }
  ]
}
```

**修改后**（基于ZSet热点数据）：
```json
{
  "hotspots": [
    {
      "dimensionValue": "192.168.1.1",
      "accessCount": 1000,
      "dimension": "ip"
    }
  ],
  "dataSource": "Redis ZSet热点统计"
}
```

### 采样统计数据

**修改前**（基于基础统计）：
```json
{
  "sampleRate": "1%",
  "totalRequests": 10000,
  "blockedRequests": 500,
  "dataSource": "基础统计数据"
}
```

**修改后**（基于实际采样）：
```json
{
  "sampleRate": "1%",
  "actualSamples": 100,
  "estimatedTotal": 10000,
  "estimatedBlocked": 50,
  "estimatedBlockRate": 5.0,
  "dataSource": "实际采样数据",
  "note": "基于实际采样数据估算的总体统计"
}
```

## Redis键对应关系

### 热点统计键
```
rate_limit:hotspot_stats:rule1:ip     # ZSet结构，存储IP访问次数
rate_limit:hotspot_stats:rule1:user   # ZSet结构，存储用户访问次数
```

### 采样统计键
```
rate_limit:sampled_stats:rule1:total:100  # Hash结构，存储1%采样统计
├── totalSamples → 100
├── allowedSamples → 95
└── blockedSamples → 5
```

### 聚合统计键
```
rate_limit:agg_stats:rule1:total:1640995200000  # Hash结构，5分钟窗口统计
├── totalRequests → 1000
├── allowedRequests → 950
└── blockedRequests → 50
```

## 前端展示适配

### 热点统计表格
```html
<table class="table table-sm">
    <thead>
        <tr>
            <th>排名</th>
            <th>IP地址</th>
            <th>访问次数</th>  <!-- 改为访问次数，而不是总请求数 -->
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>1</td>
            <td>192.168.1.1</td>
            <td>1000</td>  <!-- 来自ZSet的score值 -->
        </tr>
    </tbody>
</table>
```

### 采样信息展示
```html
<div class="card">
    <div class="card-header">采样统计信息</div>
    <div class="card-body">
        <div class="row">
            <div class="col-md-3">实际样本: 100</div>
            <div class="col-md-3">估算总数: 10,000</div>
            <div class="col-md-3">估算阻止: 500</div>
            <div class="col-md-3">数据源: 实际采样数据</div>
        </div>
    </div>
</div>
```

## 降级策略

### 1. 热点统计降级
```java
if (hotspotData == null || hotspotData.isEmpty()) {
    result.put("hotspots", new ArrayList<>());
    result.put("error", "热点统计数据获取失败");
}
```

### 2. 采样统计降级
```java
if (sampledData.isEmpty()) {
    // 降级到基础统计
    RateLimitStats basicStats = getStats(ruleId);
    samplingInfo.put("totalRequests", basicStats.getTotalRequests());
    samplingInfo.put("dataSource", "基础统计数据");
    samplingInfo.put("note", "采样数据暂未生成，显示基础统计");
}
```

### 3. 聚合统计降级
```java
if (aggData.isEmpty()) {
    // 使用基础统计的平均值
    RateLimitStats stats = getStats(ruleId);
    window.put("totalRequests", stats.getTotalRequests() / windowCount);
    window.put("dataSource", "估算数据");
}
```

## 数据生成时机

### 1. 热点数据
- 每次请求时，通过`recordSampledAndHotspotStats`方法更新ZSet
- 使用`redisTemplate.opsForZSet().incrementScore()`累加访问次数

### 2. 采样数据
- 1%概率触发采样记录
- 通过`ThreadLocalRandom.current().nextInt(100) == 0`判断

### 3. 聚合数据
- 按5分钟时间窗口聚合
- 使用时间对齐算法确保窗口边界一致

## 总结

这次修改真正实现了基于优化数据结构的统计查询：

1. **热点统计**: 直接从Redis ZSet读取，按访问频率排序
2. **采样统计**: 从实际采样数据估算总体统计
3. **聚合统计**: 基于时间窗口的聚合数据
4. **降级保护**: 当优化数据不可用时，自动降级到基础统计

现在的实现真正体现了"优化模式"的价值，既减少了Redis键数量，又提供了有意义的统计信息。
