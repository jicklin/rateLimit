<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>优化统计 - 限流管理</title>
    <link rel="stylesheet" href="/static/css/bootstrap.min.css">
    <link rel="stylesheet" href="/static/css/style.css">
    <script src="/static/js/jquery.min.js"></script>
    <script src="/static/js/bootstrap.min.js"></script>
    <script src="/static/js/chart.min.js"></script>
</head>
<body>
    <div class="container-fluid">
        <div class="row">
            <div class="col-md-2">
                <#include "sidebar.ftl">
            </div>
            <div class="col-md-10">
                <div class="main-content">
                    <h2>优化统计</h2>

                    <!-- 统计模式信息 -->
                    <div class="card mb-4">
                        <div class="card-header">
                            <h5>统计模式信息</h5>
                        </div>
                        <div class="card-body">
                            <div id="modeInfo">
                                <div class="text-center">
                                    <div class="spinner-border" role="status">
                                        <span class="sr-only">加载中...</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 规则选择 -->
                    <div class="card mb-4">
                        <div class="card-header">
                            <h5>选择规则</h5>
                        </div>
                        <div class="card-body">
                            <div class="form-group">
                                <label for="ruleSelect">限流规则:</label>
                                <select id="ruleSelect" class="form-control">
                                    <option value="">请选择规则</option>
                                    <#list rules as rule>
                                        <option value="${rule.id}">${rule.name}</option>
                                    </#list>
                                </select>
                            </div>
                            <button id="loadStatsBtn" class="btn btn-primary" disabled>加载统计数据</button>
                        </div>
                    </div>

                    <!-- 详细统计 -->
                    <div class="card mb-4" id="detailedStatsCard" style="display: none;">
                        <div class="card-header">
                            <h5>详细统计</h5>
                        </div>
                        <div class="card-body">
                            <div id="detailedStatsContent">
                                <!-- 详细统计内容将在这里显示 -->
                            </div>
                        </div>
                    </div>

                    <!-- 热点统计 -->
                    <div class="card mb-4" id="hotspotStatsCard" style="display: none;">
                        <div class="card-header">
                            <h5>热点统计</h5>
                            <div class="btn-group float-right" role="group">
                                <button type="button" class="btn btn-sm btn-outline-primary" onclick="loadHotspotStats('ip')">Top IP</button>
                                <button type="button" class="btn btn-sm btn-outline-primary" onclick="loadHotspotStats('user')">Top 用户</button>
                            </div>
                        </div>
                        <div class="card-body">
                            <div id="hotspotStatsContent">
                                <!-- 热点统计内容将在这里显示 -->
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script>
        let currentRuleId = '';
        let isOptimizedMode = false;

        $(document).ready(function() {
            loadModeInfo();

            $('#ruleSelect').change(function() {
                currentRuleId = $(this).val();
                $('#loadStatsBtn').prop('disabled', !currentRuleId);
                hideStatsCards();
            });

            $('#loadStatsBtn').click(function() {
                if (currentRuleId) {
                    loadDetailedStats(currentRuleId);
                    if (isOptimizedMode) {
                        loadHotspotStats('ip');
                    }
                }
            });
        });

        function loadModeInfo() {
            $.get('/ratelimit/api/stats/mode')
                .done(function(data) {
                    isOptimizedMode = data.optimized;
                    displayModeInfo(data);
                })
                .fail(function() {
                    $('#modeInfo').html('<div class="alert alert-danger">加载模式信息失败</div>');
                });
        }

        function displayModeInfo(data) {
            let html = `
                <div class="row">
                    <div class="col-md-6">
                        <h6>当前模式</h6>
                        <span class="badge badge-${data.optimized ? 'success' : 'info'} badge-lg">
                            ${data.mode}
                        </span>
                        <p class="mt-2">${data.description}</p>
                    </div>
                    <div class="col-md-6">
                        <h6>功能特性</h6>
                        <ul class="list-unstyled">
            `;

            data.features.forEach(function(feature) {
                html += `<li><i class="text-success">✓</i> ${feature}</li>`;
            });

            html += `
                        </ul>
                    </div>
                </div>
            `;

            $('#modeInfo').html(html);
        }

        function loadDetailedStats(ruleId) {
            $('#detailedStatsContent').html('<div class="text-center"><div class="spinner-border"></div></div>');
            $('#detailedStatsCard').show();

            $.get(`/ratelimit/api/stats/${ruleId}/detailed`)
                .done(function(data) {
                    displayDetailedStats(data);
                })
                .fail(function(xhr) {
                    let error = xhr.responseJSON || {error: '加载失败'};
                    $('#detailedStatsContent').html(`<div class="alert alert-danger">${error.error}</div>`);
                });
        }

        function displayDetailedStats(data) {
            let html = '';

            if (data.mode === 'optimized') {
                html = `
                    <div class="alert alert-success">
                        <strong>${data.data.mode}</strong> - ${data.data.description}
                    </div>

                    <!-- 采样信息 -->
                    <div class="card mb-3">
                        <div class="card-header">
                            <h6>采样统计信息</h6>
                        </div>
                        <div class="card-body">
                            <div class="row">
                                <div class="col-md-3">
                                    <strong>采样率:</strong> ${data.data.samplingInfo.sampleRate}
                                </div>
                                <div class="col-md-3">
                                    <strong>总请求:</strong> ${data.data.samplingInfo.totalRequests}
                                </div>
                                <div class="col-md-3">
                                    <strong>阻止请求:</strong> ${data.data.samplingInfo.blockedRequests}
                                </div>
                                <div class="col-md-3">
                                    <strong>阻止率:</strong> ${data.data.samplingInfo.blockRate.toFixed(2)}%
                                </div>
                            </div>
                            <p class="mt-2 mb-0 text-muted">${data.data.samplingInfo.note}</p>
                        </div>
                    </div>

                    <!-- 热点统计 -->
                    <div class="row">
                        <div class="col-md-6">
                            <div class="card">
                                <div class="card-header">
                                    <h6>热点IP (Top ${data.data.hotspotIps.topN})</h6>
                                </div>
                                <div class="card-body">
                                    <div id="hotspotIpsTable"></div>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-6">
                            <div class="card">
                                <div class="card-header">
                                    <h6>热点用户 (Top ${data.data.hotspotUsers.topN})</h6>
                                </div>
                                <div class="card-body">
                                    <div id="hotspotUsersTable"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                `;

                // 显示热点IP表格
                setTimeout(() => {
                    displayHotspotTable('hotspotIpsTable', data.data.hotspotIps.hotspots, 'IP地址');
                    displayHotspotTable('hotspotUsersTable', data.data.hotspotUsers.hotspots, '用户ID');
                }, 100);

            } else {
                html = `
                    <div class="alert alert-info">
                        <strong>标准模式</strong> - 完整详细统计
                    </div>
                    <div class="table-responsive">
                        <table class="table table-striped">
                            <thead>
                                <tr>
                                    <th>维度</th>
                                    <th>标识</th>
                                    <th>总请求</th>
                                    <th>允许</th>
                                    <th>阻止</th>
                                    <th>阻止率</th>
                                </tr>
                            </thead>
                            <tbody>
                `;

                data.data.forEach(function(stat) {
                    html += `
                        <tr>
                            <td>${stat.dimension}</td>
                            <td>${stat.dimensionValue}</td>
                            <td>${stat.totalRequests}</td>
                            <td>${stat.allowedRequests}</td>
                            <td>${stat.blockedRequests}</td>
                            <td>${stat.blockRate.toFixed(2)}%</td>
                        </tr>
                    `;
                });

                html += `
                            </tbody>
                        </table>
                    </div>
                `;
            }

            $('#detailedStatsContent').html(html);
        }

        function displayHotspotTable(containerId, hotspots, labelName) {
            let html = '';

            if (hotspots && hotspots.length > 0) {
                html = `
                    <div class="table-responsive">
                        <table class="table table-sm">
                            <thead>
                                <tr>
                                    <th>排名</th>
                                    <th>${labelName}</th>
                                    <th>访问次数</th>
                                </tr>
                            </thead>
                            <tbody>
                `;

                hotspots.forEach(function(hotspot, index) {
                    html += `
                        <tr>
                            <td>${index + 1}</td>
                            <td>${hotspot.dimensionValue}</td>
                            <td>${hotspot.accessCount || 'N/A'}</td>
                        </tr>
                    `;
                });

                html += `
                        </tbody>
                    </table>
                </div>
                `;
            } else {
                html = '<p class="text-muted">暂无热点数据</p>';
            }

            $('#' + containerId).html(html);
        }

        function loadHotspotStats(dimension) {
            if (!isOptimizedMode) {
                alert('热点统计仅在优化模式下可用');
                return;
            }

            $('#hotspotStatsContent').html('<div class="text-center"><div class="spinner-border"></div></div>');
            $('#hotspotStatsCard').show();

            $.get(`/ratelimit/api/stats/${currentRuleId}/hotspot?dimension=${dimension}&topN=20`)
                .done(function(data) {
                    displayHotspotStats(data);
                })
                .fail(function(xhr) {
                    let error = xhr.responseJSON || {error: '加载失败'};
                    $('#hotspotStatsContent').html(`<div class="alert alert-danger">${error.error}</div>`);
                });
        }

        function displayHotspotStats(data) {
            let html = `
                <h6>Top ${data.topN} ${data.dimension === 'ip' ? 'IP地址' : '用户'}</h6>
                <div class="table-responsive">
                    <table class="table table-sm">
                        <thead>
                            <tr>
                                <th>排名</th>
                                <th>${data.dimension === 'ip' ? 'IP地址' : '用户ID'}</th>
                                <th>访问次数</th>
                            </tr>
                        </thead>
                        <tbody>
            `;

            if (data.hotspots && data.hotspots.length > 0) {
                data.hotspots.forEach(function(hotspot, index) {
                    html += `
                        <tr>
                            <td>${index + 1}</td>
                            <td>${hotspot.value || hotspot}</td>
                            <td>${hotspot.score || 'N/A'}</td>
                        </tr>
                    `;
                });
            } else {
                html += '<tr><td colspan="3" class="text-center">暂无数据</td></tr>';
            }

            html += `
                        </tbody>
                    </table>
                </div>
            `;

            $('#hotspotStatsContent').html(html);
        }

        function hideStatsCards() {
            $('#detailedStatsCard').hide();
            $('#hotspotStatsCard').hide();
        }
    </script>
</body>
</html>
