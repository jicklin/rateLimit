# Properties配置使用指南

## 配置参数说明

### 1. 采样率配置

```properties
# 采样率：100表示1%采样率
rate-limit.stats.sample-rate=100
```

**说明**：
- 采样率决定了多少比例的请求会被记录到采样统计中
- 值越大，采样率越低，Redis存储开销越小
- 值越小，采样率越高，统计精度越高

**推荐配置**：
```properties
# 高流量场景（每秒>1000请求）
rate-limit.stats.sample-rate=100    # 1%采样率

# 中等流量场景（每秒100-1000请求）
rate-limit.stats.sample-rate=50     # 2%采样率

# 低流量场景（每秒<100请求）
rate-limit.stats.sample-rate=20     # 5%采样率
```

### 2. 热点统计配置

```properties
# 热点统计保留Top N
rate-limit.stats.hotspot-top-n=1000
```

**说明**：
- 在Redis ZSet中保留访问频率最高的N个IP/用户
- 超过N个时，会自动移除访问频率最低的记录
- 值越大，保留的热点数据越多，内存占用越大

**推荐配置**：
```properties
# 大规模应用（百万级用户）
rate-limit.stats.hotspot-top-n=10000

# 中等规模应用（十万级用户）
rate-limit.stats.hotspot-top-n=5000

# 小规模应用（万级用户）
rate-limit.stats.hotspot-top-n=1000
```

### 3. 聚合窗口配置

```properties
# 聚合窗口大小（分钟）
rate-limit.stats.aggregation-window-minutes=5
```

**说明**：
- 按时间窗口聚合统计数据，减少Redis键数量
- 窗口越小，统计精度越高，但Redis键数量越多
- 窗口越大，Redis键数量越少，但统计精度降低

**推荐配置**：
```properties
# 需要精细统计的场景
rate-limit.stats.aggregation-window-minutes=1   # 1分钟窗口

# 一般统计场景
rate-limit.stats.aggregation-window-minutes=5   # 5分钟窗口

# 粗粒度统计场景
rate-limit.stats.aggregation-window-minutes=15  # 15分钟窗口
```

## 配置对Redis键的影响

### 1. 采样统计键

**键格式**：`rate_limit:sampled_stats:{ruleId}:{dimension}:{sampleRate}`

**示例**：
```
rate_limit:sampled_stats:rule1:ip:100     # IP维度，1%采样率
rate_limit:sampled_stats:rule1:user:100   # 用户维度，1%采样率
```

**键数量**：固定，每个规则每个维度1个键

### 2. 热点统计键

**键格式**：`rate_limit:hotspot_stats:{ruleId}:{dimension}`

**示例**：
```
rate_limit:hotspot_stats:rule1:ip         # IP热点统计
rate_limit:hotspot_stats:rule1:user       # 用户热点统计
```

**键数量**：固定，每个规则每个维度1个键
**内存占用**：每个键最多保存`hotspot-top-n`个元素

### 3. 聚合统计键

**键格式**：`rate_limit:agg_stats:{ruleId}:{dimension}:{timestamp}`

**示例**：
```
rate_limit:agg_stats:rule1:ip:1640995200000    # 5分钟窗口聚合
rate_limit:agg_stats:rule1:user:1640995200000
```

**键数量**：随时间增长，但有TTL自动过期

## 性能影响分析

### 1. 采样率对性能的影响

| 采样率 | 写入性能 | 存储开销 | 统计精度 |
|--------|----------|----------|----------|
| 1% (100) | 最优 | 最小 | 较低 |
| 2% (50) | 优秀 | 小 | 中等 |
| 5% (20) | 良好 | 中等 | 较高 |
| 10% (10) | 一般 | 大 | 高 |

### 2. 热点统计数量对性能的影响

| Top N | 内存占用 | 查询性能 | 维护开销 |
|-------|----------|----------|----------|
| 1000 | 小 | 最优 | 小 |
| 5000 | 中等 | 优秀 | 中等 |
| 10000 | 大 | 良好 | 大 |
| 50000 | 很大 | 一般 | 很大 |

### 3. 聚合窗口对性能的影响

| 窗口大小 | Redis键数量 | 统计精度 | 查询性能 |
|----------|-------------|----------|----------|
| 1分钟 | 多 | 高 | 一般 |
| 5分钟 | 中等 | 中等 | 良好 |
| 15分钟 | 少 | 低 | 最优 |
| 60分钟 | 最少 | 最低 | 最优 |

## 配置示例

### 1. 高性能配置（优先性能）

```properties
# 启用优化统计
rate-limit.stats.optimized=true

# 低采样率，减少写入开销
rate-limit.stats.sample-rate=200    # 0.5%采样率

# 较少的热点数据，减少内存占用
rate-limit.stats.hotspot-top-n=500

# 较大的聚合窗口，减少Redis键数量
rate-limit.stats.aggregation-window-minutes=15
```

### 2. 平衡配置（性能与精度平衡）

```properties
# 启用优化统计
rate-limit.stats.optimized=true

# 中等采样率
rate-limit.stats.sample-rate=100    # 1%采样率

# 中等热点数据量
rate-limit.stats.hotspot-top-n=1000

# 中等聚合窗口
rate-limit.stats.aggregation-window-minutes=5
```

### 3. 高精度配置（优先统计精度）

```properties
# 启用优化统计
rate-limit.stats.optimized=true

# 高采样率，提高统计精度
rate-limit.stats.sample-rate=20     # 5%采样率

# 更多热点数据，提供更全面的热点分析
rate-limit.stats.hotspot-top-n=5000

# 较小的聚合窗口，提高时间精度
rate-limit.stats.aggregation-window-minutes=1
```

## 动态调整建议

### 1. 根据流量调整采样率

```bash
# 监控当前QPS
# 如果QPS > 1000，建议采样率 ≤ 1%
# 如果QPS < 100，可以采样率 ≥ 5%
```

### 2. 根据内存使用调整热点数量

```bash
# 监控Redis内存使用
# 如果内存紧张，减少hotspot-top-n
# 如果内存充足，可以增加hotspot-top-n
```

### 3. 根据查询需求调整聚合窗口

```bash
# 如果需要分钟级统计，使用1-5分钟窗口
# 如果只需要小时级统计，使用15-60分钟窗口
```

## 配置验证

### 1. 检查配置是否生效

```java
@Autowired
private OptimizedStatsConfig statsConfig;

public void checkConfig() {
    logger.info("采样率: {}", statsConfig.getSampleRate());
    logger.info("热点Top N: {}", statsConfig.getHotspotTopN());
    logger.info("聚合窗口: {}分钟", statsConfig.getAggregationWindowMinutes());
}
```

### 2. 监控Redis键数量

```bash
# 检查采样统计键
redis-cli keys "rate_limit:sampled_stats:*" | wc -l

# 检查热点统计键
redis-cli keys "rate_limit:hotspot_stats:*" | wc -l

# 检查聚合统计键
redis-cli keys "rate_limit:agg_stats:*" | wc -l
```

### 3. 监控内存使用

```bash
# 检查Redis内存使用
redis-cli info memory

# 检查特定键的内存使用
redis-cli memory usage "rate_limit:hotspot_stats:rule1:ip"
```

## 总结

通过合理配置这些参数，可以在统计精度和系统性能之间找到最佳平衡点：

1. **采样率**：控制统计精度和写入性能
2. **热点Top N**：控制内存使用和热点分析深度
3. **聚合窗口**：控制Redis键数量和时间精度

建议根据实际业务场景和系统资源情况，选择合适的配置组合。
