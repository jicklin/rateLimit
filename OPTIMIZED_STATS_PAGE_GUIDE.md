# 优化统计页面使用指南

## 页面访问

### 1. 直接访问
```
http://localhost:8080/ratelimit/optimized-stats
```

### 2. 从导航菜单访问
在限流管理系统的导航栏中点击"优化统计"链接。

## 页面功能

### 1. 统计模式检测
页面会自动检测当前使用的统计模式：
- **优化模式**: 显示绿色徽章，支持采样、热点、聚合统计
- **标准模式**: 显示蓝色徽章，使用传统的详细统计

### 2. 规则选择
- 下拉菜单显示所有可用的限流规则
- 选择规则后，"加载统计数据"按钮变为可用状态

### 3. 优化统计展示

#### 采样统计信息
```json
{
  "sampleRate": "1%",
  "dataSource": "实际采样数据",
  "actualSamples": 100,
  "estimatedTotal": 10000,
  "estimatedBlocked": 500,
  "estimatedBlockRate": 5.0
}
```

#### 热点统计
- **热点IP**: 显示访问频率最高的Top 20 IP地址
- **热点用户**: 显示访问频率最高的Top 20 用户

#### 聚合统计
- 按5分钟时间窗口聚合的统计数据
- 使用Chart.js绘制趋势图表
- 显示总请求数和阻止请求数的变化趋势

## API接口

### 1. 检测统计模式
```
GET /ratelimit/api/stats/mode
```

**响应示例**:
```json
{
  "optimized": true,
  "mode": "优化模式",
  "description": "基于采样和热点的优化统计模式"
}
```

### 2. 获取优化统计数据
```
GET /ratelimit/api/stats/{ruleId}/detailed
```

**响应示例**:
```json
{
  "mode": "optimized",
  "data": {
    "mode": "optimized",
    "description": "基于采样和热点的优化统计模式",
    "samplingInfo": {
      "sampleRate": "1%",
      "dataSource": "实际采样数据",
      "actualSamples": 100,
      "estimatedTotal": 10000
    },
    "hotspotIps": {
      "dimension": "ip",
      "topN": 20,
      "hotspots": [
        {
          "dimensionValue": "192.168.1.1",
          "accessCount": 1000,
          "dimension": "ip"
        }
      ]
    },
    "aggregatedStats": {
      "timeWindows": [
        {
          "timeLabel": "10:20",
          "totalRequests": 1000,
          "blockedRequests": 50
        }
      ]
    }
  }
}
```

## 页面结构

### HTML结构
```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <title>优化统计分析 - 限流管理</title>
    <link href="/css/ratelimit.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <div class="container">
        <header class="header">
            <h1>限流管理系统</h1>
            <nav class="nav">
                <a href="/ratelimit/optimized-stats" class="nav-link active">优化统计</a>
            </nav>
        </header>
        <main class="main">
            <!-- 页面内容 -->
        </main>
    </div>
</body>
</html>
```

### CSS样式
页面使用`/css/ratelimit.css`样式文件，包含：
- `.container`: 主容器样式
- `.header`: 页面头部样式
- `.nav`: 导航菜单样式
- `.card`: 卡片容器样式
- `.stats-table`: 统计表格样式
- `.success-message`, `.warning-message`: 消息提示样式

### JavaScript功能
- 使用原生JavaScript和Axios库
- 自动检测统计模式
- 动态加载和展示统计数据
- 使用Chart.js绘制图表

## 使用流程

### 1. 页面加载
1. 自动检测统计模式
2. 显示模式信息和功能特性
3. 加载可用的限流规则列表

### 2. 查看统计
1. 从下拉菜单选择限流规则
2. 点击"加载统计数据"按钮
3. 系统自动加载并展示优化统计数据

### 3. 数据展示
1. **采样信息**: 显示采样率、数据源、估算统计
2. **热点统计**: 表格形式显示Top访问者
3. **聚合统计**: 图表形式显示时间趋势

## 错误处理

### 1. 模式检测失败
```html
<div class="error">加载模式信息失败</div>
```

### 2. 统计数据加载失败
```html
<div class="error">加载失败</div>
```

### 3. 优化模式未启用
```html
<div class="warning-message">
    <strong>优化模式未启用</strong>
    <p>当前使用标准统计模式。要启用优化统计，请在配置中设置：</p>
    <code>rate-limit.stats.optimized=true</code>
</div>
```

## 配置要求

### 1. 启用优化模式
```yaml
rate-limit:
  stats:
    optimized: true
    sample-rate: 100  # 1%采样率
    hotspot-top-n: 1000
    aggregation-window-minutes: 5
```

### 2. 依赖库
- **Axios**: 用于HTTP请求
- **Chart.js**: 用于图表绘制
- **Bootstrap CSS**: 用于样式（可选）

## 浏览器兼容性

- Chrome 60+
- Firefox 55+
- Safari 12+
- Edge 79+

支持现代浏览器的ES6语法和Fetch API。

## 总结

优化统计页面提供了：

1. **智能模式检测**: 自动识别当前统计模式
2. **直观数据展示**: 采样、热点、聚合统计的可视化
3. **实时数据加载**: 动态获取最新的统计信息
4. **友好的用户界面**: 清晰的布局和交互设计
5. **错误处理机制**: 完善的错误提示和降级处理

通过这个页面，用户可以直观地了解优化统计模式的效果和价值。
