# FreeMarker与JavaScript模板字符串冲突修复

## 问题描述

在FreeMarker模板文件中，JavaScript的ES6模板字符串语法与FreeMarker的变量语法冲突：

### 冲突语法
```javascript
// JavaScript ES6模板字符串
let html = `<div>${variable}</div>`;

// FreeMarker变量语法
<div>${freemarkerVariable}</div>
```

两者都使用`${}`语法，导致FreeMarker解析器无法正确处理JavaScript代码。

## 解决方案

### 1. 使用字符串拼接替代模板字符串

**修改前**：
```javascript
let html = `
    <div class="card">
        <h4>${title}</h4>
        <p>${description}</p>
        <span class="badge ${type}">${status}</span>
    </div>
`;
```

**修改后**：
```javascript
let html = '<div class="card">' +
    '<h4>' + title + '</h4>' +
    '<p>' + description + '</p>' +
    '<span class="badge ' + type + '">' + status + '</span>' +
    '</div>';
```

### 2. 修复箭头函数

**修改前**：
```javascript
setTimeout(() => {
    displayChart();
}, 100);
```

**修改后**：
```javascript
setTimeout(function() {
    displayChart();
}, 100);
```

### 3. 修复可选链操作符

**修改前**：
```javascript
let errorMsg = error.response?.data?.error || '加载失败';
```

**修改后**：
```javascript
let errorMsg = (error.response && error.response.data && error.response.data.error) || '加载失败';
```

## 具体修复示例

### 1. displayModeInfo函数

**修改前**：
```javascript
function displayModeInfo(data) {
    let html = `
        <div class="stats-info">
            <span class="badge ${data.optimized ? 'success' : 'info'}">
                ${data.mode}
            </span>
            <p>${data.description || '标准统计模式'}</p>
        </div>
    `;
    document.getElementById('modeInfo').innerHTML = html;
}
```

**修改后**：
```javascript
function displayModeInfo(data) {
    let html = '<div class="stats-info">' +
        '<span class="badge ' + (data.optimized ? 'success' : 'info') + '">' +
        data.mode +
        '</span>' +
        '<p>' + (data.description || '标准统计模式') + '</p>' +
        '</div>';
    document.getElementById('modeInfo').innerHTML = html;
}
```

### 2. displayHotspotTable函数

**修改前**：
```javascript
hotspots.forEach(function(hotspot, index) {
    html += `
        <tr>
            <td>${index + 1}</td>
            <td>${hotspot.dimensionValue}</td>
            <td>${hotspot.accessCount || 'N/A'}</td>
        </tr>
    `;
});
```

**修改后**：
```javascript
hotspots.forEach(function(hotspot, index) {
    html += '<tr>' +
        '<td>' + (index + 1) + '</td>' +
        '<td>' + hotspot.dimensionValue + '</td>' +
        '<td>' + (hotspot.accessCount || 'N/A') + '</td>' +
        '</tr>';
});
```

### 3. axios请求URL

**修改前**：
```javascript
axios.get(`/ratelimit/api/stats/${ruleId}/detailed`)
```

**修改后**：
```javascript
axios.get('/ratelimit/api/stats/' + ruleId + '/detailed')
```

## 替代方案

### 1. 使用FreeMarker的noparse指令

```html
<script>
<#noparse>
    let html = `<div>${variable}</div>`;
</#noparse>
</script>
```

### 2. 将JavaScript代码移到外部文件

```html
<!-- 在FTL文件中 -->
<script src="/js/optimized-stats.js"></script>

<!-- 在外部JS文件中 -->
let html = `<div>${variable}</div>`;
```

### 3. 使用不同的分隔符

```javascript
// 使用FreeMarker的自定义分隔符
<#setting interpolation_syntax="dollar">
// 或者
<#setting interpolation_syntax="square_bracket">
```

## 最佳实践

### 1. 在FTL文件中避免ES6语法
- 不使用模板字符串
- 不使用箭头函数
- 不使用可选链操作符
- 不使用解构赋值

### 2. 使用传统JavaScript语法
- 使用字符串拼接
- 使用function关键字
- 使用条件判断代替可选链
- 使用传统的对象访问方式

### 3. 代码组织建议
- 简单的交互逻辑可以内嵌在FTL中
- 复杂的JavaScript逻辑建议放在外部文件
- 使用构建工具处理ES6到ES5的转换

## 兼容性考虑

### 1. 浏览器兼容性
修改后的代码兼容更多浏览器：
- IE 11+
- Chrome 30+
- Firefox 25+
- Safari 9+

### 2. FreeMarker版本
适用于所有FreeMarker版本，无需特殊配置。

## 总结

通过将ES6模板字符串改为传统的字符串拼接，成功解决了FreeMarker与JavaScript的语法冲突问题：

1. **解决冲突**: 避免了`${}`语法冲突
2. **保持功能**: 所有JavaScript功能正常工作
3. **提高兼容性**: 支持更多浏览器
4. **易于维护**: 代码结构清晰，便于调试

这种修复方式确保了FreeMarker模板能够正确解析，同时JavaScript代码也能正常执行。
