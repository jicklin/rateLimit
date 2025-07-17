<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>限流管理系统</title>
    <link href="/css/ratelimit.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
</head>
<body>
    <div class="container">
        <header class="header">
            <h1>限流管理系统</h1>
            <nav class="nav">
                <a href="/ratelimit/" class="nav-link active">首页</a>
                <a href="/ratelimit/config" class="nav-link">配置管理</a>
                <a href="/ratelimit/stats" class="nav-link">统计分析</a>
                <a href="/ratelimit/optimized-stats" class="nav-link">优化统计</a>

            </nav>
        </header>

        <main class="main">
            <!-- 全局统计卡片 -->
            <div class="stats-cards">
                <div class="card">
                    <h3>总请求数</h3>
                    <div class="stat-value">${globalStats.totalRequests!0}</div>
                </div>
                <div class="card">
                    <h3>允许请求</h3>
                    <div class="stat-value success">${globalStats.totalAllowed!0}</div>
                </div>
                <div class="card">
                    <h3>阻止请求</h3>
                    <div class="stat-value danger">${globalStats.totalBlocked!0}</div>
                </div>
                <div class="card">
                    <h3>阻止率</h3>
                    <div class="stat-value warning">${globalStats.globalBlockRate!0}%</div>
                </div>
                <div class="card">
                    <h3>活跃规则</h3>
                    <div class="stat-value">${globalStats.activeRules!0}/${globalStats.totalRules!0}</div>
                </div>
            </div>

            <!-- 操作按钮 -->
            <div class="actions">
                <button class="btn btn-primary" onclick="location.href='/ratelimit/config'">新增规则</button>
                <button class="btn btn-warning" onclick="resetAllStats()">重置统计</button>
                <button class="btn btn-danger" onclick="resetRateLimit()">重置限流</button>
                <button class="btn btn-info" onclick="refreshData()">刷新数据</button>
                <button class="btn btn-secondary" onclick="generateTestData()">生成测试数据</button>
                <button class="btn btn-default" onclick="debugStatus()">调试状态</button>
            </div>

            <!-- 规则列表 -->
            <div class="section">
                <h2>限流规则</h2>
                <div class="table-container">
                    <table class="table">
                        <thead>
                            <tr>
                                <th>规则名称</th>
                                <th>路径模式</th>
                                <th>HTTP方法</th>
                                <th>限流维度</th>
                                <th>令牌桶配置</th>
                                <th>IP限流</th>
                                <th>用户限流</th>
                                <th>状态</th>
                                <th>优先级</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            <#list rules as rule>
                            <tr>
                                <td>
                                    <div class="rule-name">${rule.name!''}</div>
                                    <div class="rule-desc">${rule.description!''}</div>
                                </td>
                                <td><code>${rule.pathPattern!''}</code></td>
                                <td>
                                    <#if rule.httpMethods??>
                                        <#list rule.httpMethods as method>
                                            <span class="method-tag">${method.method}</span>
                                        </#list>
                                    <#else>
                                        <span class="method-tag">ALL</span>
                                    </#if>
                                </td>
                                <td>
                                    <div class="dimension-badges">
                                        <span class="type-badge type-path">路径限流</span>
                                        <#if rule.enableIpLimit>
                                            <span class="type-badge type-ip">IP限流</span>
                                        </#if>
                                        <#if rule.enableUserLimit>
                                            <span class="type-badge type-user">用户限流</span>
                                        </#if>
                                    </div>
                                </td>
                                <td>
                                    <div class="bucket-config">
                                        <div>容量: ${rule.bucketCapacity}</div>
                                        <div>速率: ${rule.refillRate}/s</div>
                                        <div>窗口: ${rule.timeWindow}s</div>
                                    </div>
                                </td>
                                <td>
                                    <#if rule.enableIpLimit>
                                        <span class="status-enabled">启用</span>
                                        <div class="limit-config">限制: ${rule.ipRequestLimit!rule.refillRate}</div>
                                        <#if rule.ipBucketCapacity??>
                                            <div class="limit-config">容量: ${rule.ipBucketCapacity}</div>
                                        </#if>
                                    <#else>
                                        <span class="status-disabled">禁用</span>
                                    </#if>
                                </td>
                                <td>
                                    <#if rule.enableUserLimit>
                                        <span class="status-enabled">启用</span>
                                        <div class="limit-config">限制: ${rule.userRequestLimit!rule.refillRate}</div>
                                        <#if rule.userBucketCapacity??>
                                            <div class="limit-config">容量: ${rule.userBucketCapacity}</div>
                                        </#if>
                                    <#else>
                                        <span class="status-disabled">禁用</span>
                                    </#if>
                                </td>
                                <td>
                                    <#if rule.enabled>
                                        <span class="status-enabled">启用</span>
                                    <#else>
                                        <span class="status-disabled">禁用</span>
                                    </#if>
                                </td>
                                <td>${rule.priority}</td>
                                <td>
                                    <div class="action-buttons">
                                        <button class="btn btn-sm btn-info" onclick="viewStats('${rule.id}')">统计</button>
                                        <button class="btn btn-sm btn-warning" onclick="editRule('${rule.id}')">编辑</button>
                                        <#if rule.enabled>
                                            <button class="btn btn-sm btn-secondary" onclick="toggleRule('${rule.id}', false)">禁用</button>
                                        <#else>
                                            <button class="btn btn-sm btn-success" onclick="toggleRule('${rule.id}', true)">启用</button>
                                        </#if>
                                        <button class="btn btn-sm btn-danger" onclick="deleteRule('${rule.id}')">删除</button>
                                    </div>
                                </td>
                            </tr>
                            </#list>
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- 统计信息 -->
            <div class="section">
                <h2>规则统计</h2>
                <div class="stats-grid">
                    <#list stats as stat>
                    <div class="stat-card">
                        <h4>${stat.ruleName!''}</h4>
                        <div class="stat-row">
                            <span>总请求:</span>
                            <span>${stat.totalRequests}</span>
                        </div>
                        <div class="stat-row">
                            <span>允许:</span>
                            <span class="success">${stat.allowedRequests}</span>
                        </div>
                        <div class="stat-row">
                            <span>阻止:</span>
                            <span class="danger">${stat.blockedRequests}</span>
                        </div>
                        <div class="stat-row">
                            <span>阻止率:</span>
                            <span class="warning">${mathUtils.format(stat.blockRate!0)}%</span>
                        </div>
                        <div class="stat-row">
                            <span>请求频率:</span>
                            <span>${mathUtils.format(stat.requestRate!0)}/s</span>
                        </div>
                        <div class="stat-actions">
                            <button class="btn btn-sm btn-info" onclick="viewDetailedStats('${stat.ruleId}')">详细统计</button>
                            <button class="btn btn-sm btn-warning" onclick="resetStats('${stat.ruleId}')">重置</button>
                        </div>
                    </div>
                    </#list>
                </div>
            </div>
        </main>
    </div>

    <!-- 详细统计模态框 -->
    <div id="detailedStatsModal" class="modal">
        <div class="modal-content large">
            <div class="modal-header">
                <h3 id="modalTitle">详细统计信息</h3>
                <span class="close" onclick="closeModal()">&times;</span>
            </div>
            <div class="modal-body">
                <div class="modal-tabs">
                    <button class="tab-button active" onclick="showModalTab('summary')">汇总</button>
                    <button class="tab-button" onclick="showModalTab('ip')">IP详情</button>
                    <button class="tab-button" onclick="showModalTab('user')">用户详情</button>
                </div>
                <div id="summaryTab" class="tab-content active">
                    <div id="summaryContent">
                        <div class="loading">加载汇总信息...</div>
                    </div>
                </div>
                <div id="ipDetailTab" class="tab-content">
                    <div id="ipDetailContent">
                        <div class="loading">加载IP统计...</div>
                    </div>
                </div>
                <div id="userDetailTab" class="tab-content">
                    <div id="userDetailContent">
                        <div class="loading">加载用户统计...</div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script src="/js/ratelimit.js"></script>
</body>
</html>
