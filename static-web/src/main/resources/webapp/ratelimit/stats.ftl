<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>限流统计分析</title>
    <link href="/css/ratelimit.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <div class="container">
        <header class="header">
            <h1>限流管理系统</h1>
            <nav class="nav">
                <a href="/ratelimit/" class="nav-link active"  >首页</a>
                <a href="/ratelimit/config" class="nav-link">配置管理</a>
                <a href="/ratelimit/stats" class="nav-link ">统计分析</a>
                <a href="/ratelimit/optimized-stats" class="nav-link">优化统计</a>

            </nav>
        </header>

        <main class="main">
            <!-- 全局统计概览 -->
            <div class="section">
                <h2>全局统计概览</h2>
                <div class="stats-cards">
                    <div class="card">
                        <h3>总请求数</h3>
                        <div class="stat-value">${globalStats.totalRequests!0}</div>
                        <div class="stat-trend">
                            <span class="trend-icon">📈</span>
                            <span>实时统计</span>
                        </div>
                    </div>
                    <div class="card">
                        <h3>允许请求</h3>
                        <div class="stat-value success">${globalStats.totalAllowed!0}</div>
                        <div class="stat-trend">
                            <span class="trend-icon">✅</span>
                            <span>通过率: ${mathUtils.format(mathUtils.percentage(globalStats.totalAllowed!0, globalStats.totalRequests!0))}%</span>
                        </div>
                    </div>
                    <div class="card">
                        <h3>阻止请求</h3>
                        <div class="stat-value danger">${globalStats.totalBlocked!0}</div>
                        <div class="stat-trend">
                            <span class="trend-icon">🚫</span>
                            <span>阻止率: ${globalStats.globalBlockRate!0}%</span>
                        </div>
                    </div>
                    <div class="card">
                        <h3>活跃规则</h3>
                        <div class="stat-value">${globalStats.activeRules!0}</div>
                        <div class="stat-trend">
                            <span class="trend-icon">⚙️</span>
                            <span>总规则: ${globalStats.totalRules!0}</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 操作按钮 -->
            <div class="actions">
                <button class="btn btn-info" onclick="refreshStats()">刷新统计</button>
                <button class="btn btn-warning" onclick="exportStats()">导出数据</button>
                <button class="btn btn-secondary" onclick="toggleAutoRefresh()">自动刷新</button>
                <select id="timeRange" onchange="changeTimeRange()">
                    <option value="5">最近5分钟 (每分钟)</option>
                    <option value="10">最近10分钟 (每分钟)</option>
                    <option value="15" selected>最近15分钟 (每分钟)</option>
                    <option value="30">最近30分钟 (每5分钟)</option>
                    <option value="60">最近1小时 (每5分钟)</option>
                    <option value="180">最近3小时 (每15分钟)</option>
                    <option value="360">最近6小时 (每15分钟)</option>
                    <option value="720">最近12小时 (每小时)</option>
                    <option value="1440">最近24小时 (每小时)</option>
                </select>
            </div>

            <!-- 图表区域 -->
            <div class="section">
                <h2>请求趋势图</h2>
                <div class="chart-info">
                    <span id="chartInfo" class="chart-info-text">数据加载中...</span>
                </div>
                <div class="chart-container">
                    <canvas id="requestTrendChart"></canvas>
                </div>
            </div>

            <!-- 规则统计详情 -->
            <div class="section">
                <h2>规则统计详情</h2>
                <div class="table-container">
                    <table class="table">
                        <thead>
                            <tr>
                                <th>规则名称</th>
                                <th>总请求数</th>
                                <th>允许请求</th>
                                <th>阻止请求</th>
                                <th>阻止率</th>
                                <th>请求频率</th>
                                <th>最后请求时间</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            <#list stats as stat>
                            <tr>
                                <td>
                                    <div class="rule-name">${stat.ruleName!''}</div>
                                    <div class="rule-id">ID: ${stat.ruleId}</div>
                                </td>
                                <td class="stat-number">${stat.totalRequests}</td>
                                <td class="stat-number success">${stat.allowedRequests}</td>
                                <td class="stat-number danger">${stat.blockedRequests}</td>
                                <td>
                                    <div class="progress-bar">
                                        <div class="progress-fill" style="width: ${mathUtils.clamp(stat.blockRate!0, 0, 100)?c}%"></div>
                                        <span class="progress-text">${mathUtils.format(stat.blockRate!0)}%</span>
                                    </div>
                                </td>
                                <td class="stat-number">${mathUtils.format(stat.requestRate!0)}/s</td>
                                <td>
                                    <#if stat.lastRequestTime gt 0>
                                        <span class="timestamp" data-timestamp="${stat.lastRequestTime?c}">
                                            ${stat.lastRequestTime?number_to_datetime?string("yyyy-MM-dd HH:mm:ss")}
                                        </span>
                                    <#else>
                                        <span class="no-data">无数据</span>
                                    </#if>
                                </td>
                                <td>
                                    <div class="action-buttons">
                                        <button class="btn btn-sm btn-info" onclick="viewDetailedStats('${stat.ruleId}')">详细统计</button>
                                        <button class="btn btn-sm btn-warning" onclick="resetRuleStats('${stat.ruleId}')">重置</button>
                                    </div>
                                </td>
                            </tr>
                            </#list>
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- IP和用户统计 -->
            <div class="section">
                <h2>维度统计分析</h2>
                <div class="tabs">
                    <button class="tab-button active" onclick="showDimensionTab('overview')">概览</button>
                    <button class="tab-button" onclick="showDimensionTab('ip')">IP统计</button>
                    <button class="tab-button" onclick="showDimensionTab('user')">用户统计</button>
                    <button class="tab-button" onclick="showDimensionTab('records')">限流记录</button>
                </div>

                <div id="overviewTab" class="tab-content active">
                    <div class="overview-actions">
                        <button class="btn btn-sm btn-info" onclick="loadDimensionOverview()">刷新概览</button>
                        <span class="overview-note">显示所有启用维度限流规则的统计概览</span>
                    </div>
                    <div class="dimension-overview">
                        <div class="overview-card">
                            <h4>IP维度统计</h4>
                            <div id="ipOverview" class="overview-content">
                                <div class="loading">加载中...</div>
                            </div>
                        </div>
                        <div class="overview-card">
                            <h4>用户维度统计</h4>
                            <div id="userOverview" class="overview-content">
                                <div class="loading">加载中...</div>
                            </div>
                        </div>
                    </div>
                </div>

                <div id="ipTab" class="tab-content">
                    <div class="dimension-controls">
                        <select id="ipRuleSelect" onchange="loadIpStats()">
                            <option value="">选择规则</option>
                            <#list stats as stat>
                            <option value="${stat.ruleId}">${stat.ruleName}</option>
                            </#list>
                        </select>
                        <input type="number" id="ipLimit" value="20" min="10" max="100" onchange="loadIpStats()">
                        <label for="ipLimit">显示数量</label>
                    </div>
                    <div id="ipStatsContent" class="dimension-content">
                        <div class="no-data">请选择规则查看IP统计</div>
                    </div>
                </div>

                <div id="userTab" class="tab-content">
                    <div class="dimension-controls">
                        <select id="userRuleSelect" onchange="loadUserStats()">
                            <option value="">选择规则</option>
                            <#list stats as stat>
                            <option value="${stat.ruleId}">${stat.ruleName}</option>
                            </#list>
                        </select>
                        <input type="number" id="userLimit" value="20" min="10" max="100" onchange="loadUserStats()">
                        <label for="userLimit">显示数量</label>
                    </div>
                    <div id="userStatsContent" class="dimension-content">
                        <div class="no-data">请选择规则查看用户统计</div>
                    </div>
                </div>

                <div id="recordsTab" class="tab-content">
                    <div class="dimension-controls">
                        <select id="recordsRuleSelect" onchange="loadRateLimitRecords()">
                            <option value="">选择规则</option>
                            <option value="recent">最近记录</option>
                            <#list stats as stat>
                            <option value="${stat.ruleId}">${stat.ruleName}</option>
                            </#list>
                        </select>
                        <input type="number" id="recordsLimit" value="50" min="10" max="200" onchange="loadRateLimitRecords()">
                        <label for="recordsLimit">显示数量</label>
                        <input type="number" id="recordsMinutes" value="60" min="5" max="1440" onchange="loadRateLimitRecords()" style="display:none;">
                        <label for="recordsMinutes" style="display:none;">时间范围(分钟)</label>
                    </div>
                    <div id="recordsContent" class="dimension-content">
                        <div class="no-data">请选择规则查看限流记录</div>
                    </div>
                </div>
            </div>
        </main>
    </div>

    <!-- 详细统计模态框 -->
    <div id="detailedStatsModal" class="modal">
        <div class="modal-content large">
            <div class="modal-header">
                <h3>详细统计信息</h3>
                <span class="close" onclick="closeModal()">&times;</span>
            </div>
            <div class="modal-body">
                <div class="modal-tabs">
                    <button class="tab-button active" onclick="showModalTab('summary')">汇总</button>
                    <button class="tab-button" onclick="showModalTab('ipDetail')">IP详情</button>
                    <button class="tab-button" onclick="showModalTab('userDetail')">用户详情</button>
                    <button class="tab-button" onclick="showModalTab('trend')">趋势图</button>
                </div>
                <div id="summaryTab" class="tab-content active">
                    <div id="summaryContent"></div>
                </div>
                <div id="ipDetailTab" class="tab-content">
                    <div id="ipDetailContent"></div>
                </div>
                <div id="userDetailTab" class="tab-content">
                    <div id="userDetailContent"></div>
                </div>
                <div id="trendTab" class="tab-content">
                    <!-- 操作按钮 -->
                    <div class="actions">
                        <select id="ruleTimeRange" onchange="changeRuleTimeRange()">
                            <option value="5">最近5分钟 (每分钟)</option>
                            <option value="10">最近10分钟 (每分钟)</option>
                            <option value="15" selected>最近15分钟 (每分钟)</option>
                            <option value="30">最近30分钟 (每5分钟)</option>
                            <option value="60">最近1小时 (每5分钟)</option>
                            <option value="180">最近3小时 (每15分钟)</option>
                            <option value="360">最近6小时 (每15分钟)</option>
                            <option value="720">最近12小时 (每小时)</option>
                            <option value="1440">最近24小时 (每小时)</option>
                        </select>
                    </div>

                    <canvas id="detailTrendChart"></canvas>
                </div>
            </div>
        </div>
    </div>

    <script>
        // 页面加载完成后初始化
        document.addEventListener('DOMContentLoaded', function() {
            // 确保Chart.js已加载
            if (typeof Chart === 'undefined') {
                console.error('Chart.js 未加载');
                return;
            }

            initCharts();
            initDetailCharts();
            loadDimensionOverview();

            // 自动刷新功能
            let autoRefreshInterval = null;
            window.toggleAutoRefresh = function() {
                if (autoRefreshInterval) {
                    clearInterval(autoRefreshInterval);
                    autoRefreshInterval = null;
                    document.querySelector('[onclick="toggleAutoRefresh()"]').textContent = '自动刷新';
                } else {
                    autoRefreshInterval = setInterval(refreshStats, 30000); // 30秒刷新一次
                    document.querySelector('[onclick="toggleAutoRefresh()"]').textContent = '停止刷新';
                }
            };
        });

        // 初始化图表
        function initCharts() {
            try {
                const canvas = document.getElementById('requestTrendChart');
                if (!canvas) {
                    console.error('找不到图表画布元素');
                    return;
                }

                const ctx = canvas.getContext('2d');
                window.requestTrendChart = new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: [],
                        datasets: [{
                            label: '总请求数',
                            data: [],
                            borderColor: 'rgb(75, 192, 192)',
                            backgroundColor: 'rgba(75, 192, 192, 0.2)',
                            tension: 0.1
                        }, {
                            label: '阻止请求数',
                            data: [],
                            borderColor: 'rgb(255, 99, 132)',
                            backgroundColor: 'rgba(255, 99, 132, 0.2)',
                            tension: 0.1
                        }]
                    },
                    options: {
                        responsive: true,
                        scales: {
                            y: {
                                beginAtZero: true
                            }
                        }
                    }
                });

                console.log('图表初始化成功');

                // 延迟加载初始数据，确保图表完全初始化
                setTimeout(() => {
                    if (typeof loadTrendData === 'function') {
                        loadTrendData();
                    }
                }, 100);

            } catch (error) {
                console.error('图表初始化失败:', error);
            }
        }

        // 初始化图表
        function initDetailCharts() {
            try {
                const canvas = document.getElementById('detailTrendChart');
                if (!canvas) {
                    console.error('找不到图表画布元素');
                    return;
                }

                const ctx = canvas.getContext('2d');
                window.detailTrendChart = new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: [],
                        datasets: [{
                            label: '总请求数',
                            data: [],
                            borderColor: 'rgb(75, 192, 192)',
                            backgroundColor: 'rgba(75, 192, 192, 0.2)',
                            tension: 0.1
                        }, {
                            label: '阻止请求数',
                            data: [],
                            borderColor: 'rgb(255, 99, 132)',
                            backgroundColor: 'rgba(255, 99, 132, 0.2)',
                            tension: 0.1
                        }]
                    },
                    options: {
                        responsive: true,
                        scales: {
                            y: {
                                beginAtZero: true
                            }
                        }
                    }
                });

            } catch (error) {
                console.error('图表初始化失败:', error);
            }
        }
    </script>
    <script src="/js/ratelimit.js"></script>
</body>
</html>
