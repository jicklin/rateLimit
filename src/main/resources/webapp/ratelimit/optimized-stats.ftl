<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
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
                <a href="/ratelimit/" class="nav-link">首页</a>
                <a href="/ratelimit/config" class="nav-link">配置管理</a>
                <a href="/ratelimit/stats" class="nav-link">统计分析</a>
                <a href="/ratelimit/optimized-stats" class="nav-link active">优化统计</a>
            </nav>
        </header>

        <main class="main">
            <h2>优化统计分析</h2>

            <!-- 统计模式信息 -->
            <div class="section">
                <h3>统计模式信息</h3>
                <div id="modeInfo" class="card">
                    <div class="loading">加载中...</div>
                </div>
            </div>

            <!-- 规则选择 -->
            <div class="section">
                <h3>选择规则</h3>
                <div class="card">
                    <div class="form-group">
                        <label for="ruleSelect">限流规则:</label>
                        <select id="ruleSelect" class="form-control">
                            <option value="">请选择规则</option>
                            <#if rules??>
                                <#list rules as rule>
                                    <option value="${rule.id}">${rule.name}</option>
                                </#list>
                            </#if>
                        </select>
                    </div>
                    <button id="loadStatsBtn" class="btn btn-primary" disabled>加载统计数据</button>
                </div>
            </div>

            <!-- 优化统计展示 -->
            <div class="section" id="optimizedStatsSection" style="display: none;">
                <h3>优化统计数据</h3>
                <div id="optimizedStatsContent">
                    <!-- 优化统计内容将在这里显示 -->
                </div>
            </div>
        </main>
    </div>

    <script>
        let currentRuleId = '';
        let isOptimizedMode = false;

        document.addEventListener('DOMContentLoaded', function() {
            loadModeInfo();

            document.getElementById('ruleSelect').addEventListener('change', function() {
                currentRuleId = this.value;
                document.getElementById('loadStatsBtn').disabled = !currentRuleId;
                hideStatsCards();
            });

            document.getElementById('loadStatsBtn').addEventListener('click', function() {
                if (currentRuleId) {
                    loadOptimizedStats(currentRuleId);
                }
            });
        });

        function loadModeInfo() {
            axios.get('/ratelimit/api/stats/mode')
                .then(function(response) {
                    isOptimizedMode = response.data.optimized;
                    displayModeInfo(response.data);
                })
                .catch(function() {
                    document.getElementById('modeInfo').innerHTML = '<div class="error">加载模式信息失败</div>';
                });
        }

        function displayModeInfo(data) {
            let html = '<div class="stats-info">' +
                '<div class="mode-info">' +
                '<h4>当前模式</h4>' +
                '<span class="badge ' + (data.optimized ? 'success' : 'info') + '">' +
                data.mode +
                '</span>' +
                '<p>' + (data.description || '标准统计模式') + '</p>' +
                '</div>' +
                '<div class="features-info">' +
                '<h4>功能特性</h4>' +
                '<ul>';

            if (data.features) {
                data.features.forEach(function(feature) {
                    html += '<li>✓ ' + feature + '</li>';
                });
            } else {
                html += '<li>✓ 完整的详细统计记录</li><li>✓ 实时统计数据</li>';
            }

            html += '</ul>' +
                '</div>' +
                '</div>';

            document.getElementById('modeInfo').innerHTML = html;
        }

        function loadOptimizedStats(ruleId) {
            document.getElementById('optimizedStatsContent').innerHTML = '<div class="loading">加载中...</div>';
            document.getElementById('optimizedStatsSection').style.display = 'block';

            axios.get('/ratelimit/api/stats/' + ruleId + '/detailed')
                .then(function(response) {
                    displayOptimizedStats(response.data);
                })
                .catch(function(error) {
                    let errorMsg = (error.response && error.response.data && error.response.data.error) || '加载失败';
                    document.getElementById('optimizedStatsContent').innerHTML = '<div class="error">' + errorMsg + '</div>';
                });
        }

        function displayOptimizedStats(data) {
            let html = '';

            if (data.mode === 'optimized' && data.data.mode === 'optimized') {
                html = '<div class="success-message">' +
                    '<strong>' + data.data.mode + '</strong> - ' + data.data.description +
                    '</div>' +

                    '<!-- 采样信息 -->' +
                    '<div class="card">' +
                    '<h4>采样统计信息</h4>' +
                    '<div class="stats-grid">' +
                    '<div class="stat-item">' +
                    '<strong>采样率:</strong> ' + data.data.samplingInfo.sampleRate +
                    '</div>' +
                    '<div class="stat-item">' +
                    '<strong>数据源:</strong> ' + data.data.samplingInfo.dataSource +
                    '</div>' +
                    '</div>' +
                    displaySamplingDetails(data.data.samplingInfo) +
                    '<p class="note">' + data.data.samplingInfo.note + '</p>' +
                    '</div>' +

                    '<!-- 热点统计 -->' +
                    '<div class="stats-grid">' +
                    '<div class="card">' +
                    '<h4>热点IP (Top ' + data.data.hotspotIps.topN + ')</h4>' +
                    '<div id="hotspotIpsTable"></div>' +
                    '</div>' +
                    '<div class="card">' +
                    '<h4>热点用户 (Top ' + data.data.hotspotUsers.topN + ')</h4>' +
                    '<div id="hotspotUsersTable"></div>' +
                    '</div>' +
                    '</div>' +

                    '<!-- 聚合统计 -->' +
                    '<div class="card">' +
                    '<h4>聚合统计 (' + data.data.aggregatedStats.description + ')</h4>' +
                    '<div id="aggregatedStatsChart"></div>' +
                    '</div>';

                // 显示热点统计表格
                setTimeout(function() {
                    displayHotspotTable('hotspotIpsTable', data.data.hotspotIps.hotspots, 'IP地址');
                    displayHotspotTable('hotspotUsersTable', data.data.hotspotUsers.hotspots, '用户ID');
                    displayAggregatedChart(data.data.aggregatedStats);
                }, 100);

            } else {
                html = '<div class="warning-message">' +
                    '<strong>优化模式未启用</strong>' +
                    '<p>当前使用标准统计模式。要启用优化统计，请在配置中设置：</p>' +
                    '<code>rate-limit.stats.optimized=true</code>' +
                    '</div>';
            }

            document.getElementById('optimizedStatsContent').innerHTML = html;
        }

        function displaySamplingDetails(samplingInfo) {
            if (samplingInfo.dataSource === '实际采样数据') {
                return '<div class="stats-grid">' +
                    '<div class="stat-item">' +
                    '<strong>实际样本:</strong> ' + samplingInfo.actualSamples +
                    '</div>' +
                    '<div class="stat-item">' +
                    '<strong>估算总数:</strong> ' + samplingInfo.estimatedTotal +
                    '</div>' +
                    '<div class="stat-item">' +
                    '<strong>估算阻止:</strong> ' + samplingInfo.estimatedBlocked +
                    '</div>' +
                    '<div class="stat-item">' +
                    '<strong>估算阻止率:</strong> ' + samplingInfo.estimatedBlockRate.toFixed(2) + '%' +
                    '</div>' +
                    '</div>';
            } else {
                return '<div class="stats-grid">' +
                    '<div class="stat-item">' +
                    '<strong>总请求:</strong> ' + samplingInfo.totalRequests +
                    '</div>' +
                    '<div class="stat-item">' +
                    '<strong>阻止请求:</strong> ' + samplingInfo.blockedRequests +
                    '</div>' +
                    '<div class="stat-item">' +
                    '<strong>阻止率:</strong> ' + samplingInfo.blockRate.toFixed(2) + '%' +
                    '</div>' +
                    '</div>';
            }
        }

        function displayHotspotTable(containerId, hotspots, labelName) {
            let html = '';

            if (hotspots && hotspots.length > 0) {
                html = '<div class="table-container">' +
                    '<table class="stats-table">' +
                    '<thead>' +
                    '<tr>' +
                    '<th>排名</th>' +
                    '<th>' + labelName + '</th>' +
                    '<th>访问次数</th>' +
                    '</tr>' +
                    '</thead>' +
                    '<tbody>';

                hotspots.forEach(function(hotspot, index) {
                    html += '<tr>' +
                        '<td>' + (index + 1) + '</td>' +
                        '<td>' + hotspot.dimensionValue + '</td>' +
                        '<td>' + (hotspot.accessCount || 'N/A') + '</td>' +
                        '</tr>';
                });

                html += '</tbody>' +
                    '</table>' +
                    '</div>';
            } else {
                html = '<p class="no-data">暂无热点数据</p>';
            }

            document.getElementById(containerId).innerHTML = html;
        }

        function displayAggregatedChart(aggregatedStats) {
            if (!aggregatedStats.timeWindows || aggregatedStats.timeWindows.length === 0) {
                document.getElementById('aggregatedStatsChart').innerHTML = '<p class="no-data">暂无聚合数据</p>';
                return;
            }

            let labels = [];
            let requests = [];
            let blocked = [];

            aggregatedStats.timeWindows.forEach(function(window) {
                labels.push(window.timeLabel);
                requests.push(window.totalRequests);
                blocked.push(window.blockedRequests);
            });

            let html = '<canvas id="aggregatedChart" width="400" height="200"></canvas>' +
                '<p class="note">' + aggregatedStats.note + '</p>';

            document.getElementById('aggregatedStatsChart').innerHTML = html;

            // 绘制图表
            setTimeout(function() {
                const ctx = document.getElementById('aggregatedChart').getContext('2d');
                new Chart(ctx, {
                    type: 'line',
                    data: {
                        labels: labels,
                        datasets: [{
                            label: '总请求',
                            data: requests,
                            borderColor: 'rgb(75, 192, 192)',
                            tension: 0.1
                        }, {
                            label: '阻止请求',
                            data: blocked,
                            borderColor: 'rgb(255, 99, 132)',
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
            }, 100);
        }

        function hideStatsCards() {
            document.getElementById('optimizedStatsSection').style.display = 'none';
        }
    </script>
</body>
</html>
