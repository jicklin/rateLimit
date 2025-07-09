# Redis限流系统

基于Redis和令牌桶算法的分布式限流系统，支持IP、用户ID、路径等多维度限流，提供完整的管理界面和统计功能。

## 功能特性

### 🚀 核心功能
- **多维度限流**: 默认基于路径限流，可选择启用IP和用户ID维度
- **多重校验**: 启用多个维度时，请求需通过所有维度的限流检查
- **令牌桶算法**: 基于Redis的分布式令牌桶实现，支持突发流量
- **Ant路径匹配**: 支持`?`、`*`、`**`通配符的路径模式匹配
- **HTTP方法过滤**: 可选择性地对特定HTTP方法进行限流
- **详细日志记录**: 记录限流阻止的详细信息，便于分析和监控

### 📊 统计分析
- **实时统计**: 请求数、阻止数、通过率等实时统计
- **维度统计**: IP和用户级别的详细统计信息
- **图表展示**: 请求趋势图和统计图表
- **数据导出**: 支持CSV格式数据导出

### 🎛️ 管理界面
- **规则管理**: 可视化的限流规则配置和管理
- **实时监控**: 限流状态和统计信息的实时监控
- **测试工具**: 内置的限流功能测试工具
- **响应式设计**: 支持PC和移动端访问

## 技术架构

### 后端技术栈
- **Spring Boot 2.6.13**: 应用框架
- **Redis**: 分布式缓存和限流状态存储
- **Freemarker**: 模板引擎
- **Jackson**: JSON序列化
- **Lua脚本**: 原子性的令牌桶操作

### 前端技术栈
- **HTML5 + CSS3**: 响应式界面
- **JavaScript (ES6)**: 交互逻辑
- **Axios**: HTTP客户端
- **Chart.js**: 图表展示

## 快速开始

### 1. 环境要求
- Java 8+
- Redis 3.0+
- Maven 3.6+

### 2. 启动Redis
```bash
redis-server
```

### 3. 配置应用
编辑 `src/main/resources/application.properties`:
```properties
# Redis配置
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.database=0

# 应用端口
server.port=8080
```

### 4. 启动应用
```bash
mvn spring-boot:run
```

### 5. 访问管理界面
- 管理首页: http://localhost:8080/ratelimit/
- 规则配置: http://localhost:8080/ratelimit/config
- 统计分析: http://localhost:8080/ratelimit/stats
- 功能测试: http://localhost:8080/test.html

## 使用指南

### 创建限流规则

1. **基本配置**
   - 规则名称: 便于识别的规则名称
   - 路径模式: 支持Ant风格通配符
     - `?`: 匹配单个字符
     - `*`: 匹配任意字符（除路径分隔符）
     - `**`: 匹配任意路径
   - HTTP方法: 可选择特定的HTTP方法

2. **限流维度**
   - **路径限流**: 默认启用，基于请求路径进行限流
   - **IP维度限流**: 可选启用，对每个IP地址单独限流
   - **用户维度限流**: 可选启用，对每个用户ID单独限流
   - **多维度校验**: 启用多个维度时，请求需要通过所有维度的检查

3. **令牌桶配置**
   - **令牌桶容量**: 最大令牌数，决定突发请求处理能力
   - **令牌补充速率**: 每秒补充的令牌数，决定平均限流速率
   - **时间窗口**: 令牌补充的时间间隔

4. **维度限流配置**
   - **IP维度限流**: 可单独配置每个IP的限流参数
   - **用户维度限流**: 可单独配置每个用户的限流参数

### 配置示例

#### 示例1: API接口限流
```
规则名称: API接口限流
路径模式: /api/**
HTTP方法: GET, POST
限流类型: IP限流
令牌桶容量: 20
令牌补充速率: 10
时间窗口: 1秒
启用IP维度限流: 是
IP请求限制: 10
```

#### 示例2: 用户操作限流
```
规则名称: 用户操作限流
路径模式: /user/*/action
HTTP方法: POST
限流类型: 用户限流
令牌桶容量: 5
令牌补充速率: 2
时间窗口: 1秒
启用用户维度限流: 是
用户请求限制: 2
```

#### 示例3: 高频接口限流
```
规则名称: 高频接口限流
路径模式: /high-frequency
HTTP方法: 全部
限流类型: 路径限流
令牌桶容量: 100
令牌补充速率: 50
时间窗口: 1秒
```

## 令牌桶算法详解

### 算法原理
令牌桶算法通过控制令牌的生成和消费来实现限流：

1. **令牌生成**: 按固定速率向桶中添加令牌
2. **令牌消费**: 每个请求消费一个令牌
3. **桶容量限制**: 桶中令牌数不超过最大容量
4. **请求处理**: 有令牌则允许请求，无令牌则拒绝

### 优势特点
- **平滑限流**: 允许突发流量，但长期平均速率受限
- **灵活配置**: 可独立配置桶容量和补充速率
- **分布式支持**: 基于Redis实现分布式一致性
- **高性能**: Lua脚本保证原子性操作

### Lua脚本实现
```lua
-- 获取当前令牌数和最后补充时间
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local current_tokens = tonumber(bucket[1]) or capacity
local last_refill = tonumber(bucket[2]) or now

-- 计算需要补充的令牌数
local elapsed = math.max(0, now - last_refill)
local tokens_to_add = math.floor(elapsed / interval * tokens)
current_tokens = math.min(capacity, current_tokens + tokens_to_add)

-- 判断是否允许请求
local allowed = 0
if current_tokens >= requested then
    current_tokens = current_tokens - requested
    allowed = 1
end

-- 更新令牌桶状态
redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill', now)
return {allowed, current_tokens}
```

## API接口

### 规则管理
- `GET /ratelimit/api/rules` - 获取所有规则
- `GET /ratelimit/api/rules/{id}` - 获取单个规则
- `POST /ratelimit/api/rules` - 创建/更新规则
- `DELETE /ratelimit/api/rules/{id}` - 删除规则
- `PUT /ratelimit/api/rules/{id}/toggle` - 启用/禁用规则

### 统计信息
- `GET /ratelimit/api/stats` - 获取所有统计
- `GET /ratelimit/api/stats/global` - 获取全局统计
- `GET /ratelimit/api/stats/{ruleId}/detailed` - 获取详细统计
- `GET /ratelimit/api/stats/{ruleId}/ip` - 获取IP统计
- `GET /ratelimit/api/stats/{ruleId}/user` - 获取用户统计

### 系统管理
- `DELETE /ratelimit/api/stats` - 重置所有统计
- `DELETE /ratelimit/api/reset` - 重置限流状态

## 测试验证

### 内置测试工具
访问 http://localhost:8080/test.html 使用内置测试工具：

1. **单个请求测试**: 发送单个请求验证限流效果
2. **批量请求测试**: 按间隔发送多个请求
3. **并发请求测试**: 同时发送多个请求
4. **场景测试**: 预设的测试场景
   - 正常访问测试
   - 突发流量测试
   - 攻击模拟测试
   - 多用户测试

### 测试接口
系统提供了多个测试接口：
- `GET /test/get` - GET请求测试
- `POST /test/post` - POST请求测试
- `GET /test/api/data` - API接口测试
- `GET /test/user/{userId}` - 用户接口测试
- `GET /test/high-frequency` - 高频接口测试

## 监控和运维

### 统计指标
- **请求总数**: 系统处理的总请求数
- **允许请求数**: 通过限流检查的请求数
- **阻止请求数**: 被限流阻止的请求数
- **阻止率**: 被阻止请求的百分比
- **请求频率**: 每秒请求数

### 维度统计
- **IP统计**: 每个IP的请求统计
- **用户统计**: 每个用户的请求统计
- **规则统计**: 每个规则的执行统计

### 数据导出
支持将统计数据导出为CSV格式，便于进一步分析。

## 扩展开发

### 添加新的限流策略
1. 实现 `RateLimitStrategy` 接口
2. 添加 `@Component` 注解
3. 实现必要的方法：
   - `generateKey()`: 生成限流键
   - `getRequestLimit()`: 获取请求限制
   - `supports()`: 检查是否支持
   - `extractIdentifier()`: 提取标识符

### 自定义统计维度
1. 扩展 `DetailedRateLimitStats` 模型
2. 修改 `RedisRateLimitStatsService` 实现
3. 更新前端展示逻辑

## 注意事项

1. **Redis连接**: 确保Redis服务正常运行且连接配置正确
2. **时钟同步**: 分布式环境下确保各节点时钟同步
3. **内存使用**: 监控Redis内存使用，及时清理过期数据
4. **性能调优**: 根据实际负载调整令牌桶参数
5. **规则优先级**: 数字越小优先级越高，合理设置规则优先级

## 许可证

MIT License

## 贡献

欢迎提交Issue和Pull Request来改进这个项目。
