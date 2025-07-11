# 高并发场景使用指南

当活动参与人数过多时，详细统计功能可能会产生大量Redis键，影响系统性能。本指南介绍如何在高并发场景下优化限流统计。

## 问题分析

### 1. Redis键数量爆炸

在标准模式下，每个IP和用户都会创建单独的统计键：

```
rate_limit:detailed_stats:rule1:ip:192.168.1.1
rate_limit:detailed_stats:rule1:ip:192.168.1.2
rate_limit:detailed_stats:rule1:user:user001
rate_limit:detailed_stats:rule1:user:user002
...
```

如果有10万用户参与活动，可能产生：
- 10万个用户统计键
- 数万个IP统计键
- 对应的维度列表键

### 2. 潜在影响

- **内存占用**: 大量Redis键占用内存
- **性能下降**: Redis操作变慢，影响限流响应时间
- **过期清理**: 大量键的过期处理增加Redis负担
- **网络开销**: 键名过长增加网络传输成本

## 解决方案

### 1. 启用优化模式

在配置文件中启用优化模式：

```yaml
rate-limit:
  stats:
    enabled: true
    optimized: true  # 启用优化模式
    max-detailed-stats: 10000  # 最大详细统计数量
    sample-rate: 100  # 采样率：1%
    hotspot-top-n: 1000  # 热点统计保留Top 1000
    aggregation-window-minutes: 5  # 聚合窗口：5分钟
```

### 2. 优化策略说明

#### Hash结构存储
当统计数量较少时（< 10000），使用Hash结构：

```
rate_limit:stats_hash:rule1:ip
  ├── 192.168.1.1:total → 100
  ├── 192.168.1.1:allowed → 95
  ├── 192.168.1.1:blocked → 5
  ├── 192.168.1.2:total → 50
  └── ...
```

**优势**: 减少键数量，提高查询效率

#### 采样统计
当统计数量过多时，使用采样统计：

```yaml
# 只记录1%的请求统计
rate_limit:sampled_stats:rule1:ip:100
  ├── totalSamples → 1000
  ├── allowedSamples → 950
  └── blockedSamples → 50
```

**优势**: 大幅减少存储开销，保持统计准确性

#### 热点统计
使用ZSet记录访问频率最高的IP/用户：

```
rate_limit:hotspot_stats:rule1:ip
  ├── 192.168.1.1 → 1000 (访问次数)
  ├── 192.168.1.2 → 800
  └── ... (只保留Top 1000)
```

**优势**: 重点关注高频访问者，便于监控和分析

#### 聚合统计
按时间窗口聚合统计数据：

```
rate_limit:agg_stats:rule1:ip:1640995200000  # 5分钟窗口
  ├── totalRequests → 50000
  ├── allowedRequests → 47500
  └── blockedRequests → 2500
```

**优势**: 减少长期存储数据量，支持趋势分析

## 配置参数详解

### 基础配置

```yaml
rate-limit:
  stats:
    optimized: true  # 是否启用优化模式
```

### 详细配置

```yaml
rate-limit:
  stats:
    optimized: true
    max-detailed-stats: 10000  # 详细统计阈值
    sample-rate: 100          # 采样率（100=1%）
    hotspot-top-n: 1000       # 热点统计数量
    aggregation-window-minutes: 5  # 聚合窗口
    retention-hours: 24       # 数据保留时间
```

### 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `optimized` | `false` | 是否启用优化模式 |
| `max-detailed-stats` | `10000` | 详细统计的最大数量阈值 |
| `sample-rate` | `100` | 采样率，100表示1%采样 |
| `hotspot-top-n` | `1000` | 热点统计保留的Top N数量 |
| `aggregation-window-minutes` | `5` | 聚合统计的时间窗口 |

## 使用建议

### 1. 根据场景选择模式

**小型活动** (< 1万用户):
```yaml
rate-limit:
  stats:
    optimized: false  # 使用标准模式
```

**中型活动** (1-10万用户):
```yaml
rate-limit:
  stats:
    optimized: true
    max-detailed-stats: 50000
    sample-rate: 50  # 2%采样
```

**大型活动** (> 10万用户):
```yaml
rate-limit:
  stats:
    optimized: true
    max-detailed-stats: 10000
    sample-rate: 100  # 1%采样
    hotspot-top-n: 500
```

### 2. 监控Redis使用情况

```bash
# 监控Redis内存使用
redis-cli info memory

# 监控键数量
redis-cli dbsize

# 查看限流相关键
redis-cli keys "rate_limit:*" | wc -l
```

### 3. 性能调优

#### Redis配置优化
```conf
# redis.conf
maxmemory 2gb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
```

#### 应用配置优化
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
    timeout: 2000ms
```

## 监控和告警

### 1. 关键指标监控

```java
// 监控Redis键数量
@Component
public class RateLimitMonitor {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Scheduled(fixedRate = 60000) // 每分钟检查
    public void monitorRedisKeys() {
        Set<String> keys = redisTemplate.keys("rate_limit:*");
        int keyCount = keys != null ? keys.size() : 0;
        
        if (keyCount > 100000) {
            logger.warn("限流Redis键数量过多: {}", keyCount);
            // 发送告警
        }
    }
}
```

### 2. 告警阈值设置

| 指标 | 告警阈值 | 处理建议 |
|------|----------|----------|
| Redis键数量 | > 100,000 | 启用优化模式 |
| Redis内存使用率 | > 80% | 增加内存或优化配置 |
| 限流响应时间 | > 100ms | 检查Redis性能 |

## 故障处理

### 1. Redis内存不足

**现象**: Redis内存使用率过高，可能出现OOM

**解决方案**:
```yaml
# 启用更激进的优化
rate-limit:
  stats:
    optimized: true
    max-detailed-stats: 5000
    sample-rate: 200  # 0.5%采样
    retention-hours: 12  # 减少保留时间
```

### 2. 限流响应慢

**现象**: 限流检查耗时过长

**解决方案**:
1. 启用优化模式
2. 减少统计精度
3. 使用Redis集群
4. 优化网络配置

### 3. 统计数据丢失

**现象**: 部分统计数据缺失

**原因**: 采样率过高或Redis键过期

**解决方案**:
```yaml
rate-limit:
  stats:
    sample-rate: 50  # 降低采样率
    retention-hours: 48  # 增加保留时间
```

## 最佳实践

1. **预估用户规模**: 根据预期用户数量选择合适的配置
2. **分阶段优化**: 从标准模式开始，根据实际情况逐步优化
3. **监控为先**: 建立完善的监控体系
4. **测试验证**: 在生产环境前充分测试
5. **备用方案**: 准备降级策略，必要时可以关闭详细统计

## 总结

通过启用优化模式，可以有效解决大量用户参与时的Redis键数量爆炸问题：

- **Hash结构**: 减少键数量，提高查询效率
- **采样统计**: 保持统计准确性，大幅减少存储开销
- **热点统计**: 重点关注高频访问者
- **聚合统计**: 支持趋势分析，减少长期存储

合理配置这些优化策略，可以在保证限流功能正常工作的同时，显著降低Redis的存储和性能压力。
