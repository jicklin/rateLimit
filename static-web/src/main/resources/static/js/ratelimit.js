// 全局变量
let currentRuleId = null;
let detailedStatsModal = null;

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 初始化模态框
    detailedStatsModal = document.getElementById('detailedStatsModal');

    // 绑定模态框关闭事件
    window.onclick = function(event) {
        if (event.target === detailedStatsModal) {
            closeModal();
        }
    };
});

// API 请求封装
const api = {
    // 获取所有规则
    getRules: () => axios.get('/ratelimit/api/rules'),

    // 获取单个规则
    getRule: (id) => axios.get(`/ratelimit/api/rules/${id}`),

    // 保存规则
    saveRule: (rule) => axios.post('/ratelimit/api/rules', rule),

    // 删除规则
    deleteRule: (id) => axios.delete(`/ratelimit/api/rules/${id}`),

    // 切换规则状态
    toggleRule: (id, enabled) => axios.put(`/ratelimit/api/rules/${id}/toggle?enabled=${enabled}`),

    // 更新优先级
    updatePriority: (id, priority) => axios.put(`/ratelimit/api/rules/${id}/priority?priority=${priority}`),

    // 获取统计信息
    getStats: () => axios.get('/ratelimit/api/stats'),

    // 获取全局统计
    getGlobalStats: () => axios.get('/ratelimit/api/stats/global'),

    // 获取详细统计
    getDetailedStats: (ruleId) => axios.get(`/ratelimit/api/stats/${ruleId}/detailed`),

    // 获取IP统计
    getIpStats: (ruleId, limit = 50) => axios.get(`/ratelimit/api/stats/${ruleId}/ip?limit=${limit}`),

    // 获取用户统计
    getUserStats: (ruleId, limit = 50) => axios.get(`/ratelimit/api/stats/${ruleId}/user?limit=${limit}`),

    // 获取限流记录
    getRateLimitRecords: (ruleId, limit = 100) => axios.get(`/ratelimit/api/records/${ruleId}?limit=${limit}`),

    // 获取最近限流记录
    getRecentRateLimitRecords: (minutes = 60, limit = 100) => axios.get(`/ratelimit/api/records/recent?minutes=${minutes}&limit=${limit}`),

    // 获取趋势数据
    getTrendData: (minutes = 60) => axios.get(`/ratelimit/api/stats/trend?minutes=${minutes}`),

    // 获取趋势数据
    getRuleTrendData: (minutes = 60,ruleId) => axios.get(`/ratelimit/api/stats/${ruleId}/trend?minutes=${minutes}`),

    // 生成测试数据
    generateTestData: () => axios.post('/ratelimit/api/test/generate-data'),

    // 调试状态
    getDebugStatus: () => axios.get('/ratelimit/api/debug/status'),

    // 获取维度概览数据
    getDimensionOverview: () => axios.get('/ratelimit/api/stats/dimension-overview'),

    // 重置统计
    resetStats: (ruleId) => axios.delete(`/ratelimit/api/stats/${ruleId}`),

    // 重置所有统计
    resetAllStats: () => axios.delete('/ratelimit/api/stats'),

    // 重置限流
    resetRateLimit: () => axios.delete('/ratelimit/api/reset')
};

// 工具函数
const utils = {
    // 显示消息
    showMessage: function(message, type = 'info') {
        const alertClass = type === 'error' ? 'danger' : type;
        const alertHtml = `
            <div class="alert alert-${alertClass}" style="position: fixed; top: 20px; right: 20px; z-index: 9999; padding: 1rem; border-radius: 4px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); background: ${type === 'error' ? '#f8d7da' : type === 'success' ? '#d4edda' : '#d1ecf1'}; color: ${type === 'error' ? '#721c24' : type === 'success' ? '#155724' : '#0c5460'}; border: 1px solid ${type === 'error' ? '#f5c6cb' : type === 'success' ? '#c3e6cb' : '#bee5eb'};">
                ${message}
                <button type="button" style="float: right; background: none; border: none; font-size: 1.2rem; cursor: pointer; margin-left: 1rem;" onclick="this.parentElement.remove()">&times;</button>
            </div>
        `;
        document.body.insertAdjacentHTML('beforeend', alertHtml);

        // 3秒后自动消失
        setTimeout(() => {
            const alert = document.querySelector('.alert');
            if (alert) alert.remove();
        }, 3000);
    },

    // 格式化时间
    formatTime: function(timestamp) {
        if (!timestamp) return '无数据';
        return new Date(timestamp).toLocaleString('zh-CN');
    },

    // 格式化数字
    formatNumber: function(num, decimals = 2) {
        if (num == null || isNaN(num)) {
            return '0';
        }
        return Number(num).toFixed(decimals);
    },

    // 确认对话框
    confirm: function(message) {
        return window.confirm(message);
    }
};

// 规则管理功能
const ruleManager = {
    // 编辑规则
    edit: function(ruleId) {
        window.location.href = `/ratelimit/config?id=${ruleId}`;
    },

    // 删除规则
    delete: function(ruleId) {
        if (!utils.confirm('确定要删除这个规则吗？')) return;

        api.deleteRule(ruleId)
            .then(response => {
                if (response.data.success) {
                    utils.showMessage('规则删除成功', 'success');
                    setTimeout(() => location.reload(), 1000);
                } else {
                    utils.showMessage(response.data.message, 'error');
                }
            })
            .catch(error => {
                utils.showMessage('删除失败: ' + error.message, 'error');
            });
    },

    // 切换规则状态
    toggle: function(ruleId, enabled) {
        const action = enabled ? '启用' : '禁用';

        api.toggleRule(ruleId, enabled)
            .then(response => {
                if (response.data.success) {
                    utils.showMessage(`规则${action}成功`, 'success');
                    setTimeout(() => location.reload(), 1000);
                } else {
                    utils.showMessage(response.data.message, 'error');
                }
            })
            .catch(error => {
                utils.showMessage(`${action}失败: ` + error.message, 'error');
            });
    },

    // 保存规则
    save: function(formData) {
        return api.saveRule(formData);
    },

    // 加载规则数据到表单
    loadToForm: function(ruleId) {
        api.getRule(ruleId)
            .then(response => {
                const rule = response.data;
                if (rule) {
                    // 填充表单数据
                    document.getElementById('ruleId').value = rule.id || '';
                    document.getElementById('name').value = rule.name || '';
                    document.getElementById('description').value = rule.description || '';
                    document.getElementById('pathPattern').value = rule.pathPattern || '';
                    document.getElementById('priority').value = rule.priority || 100;
                    document.getElementById('enabled').checked = rule.enabled !== false;



                    // 设置HTTP方法
                    if (rule.httpMethods) {
                        rule.httpMethods.forEach(method => {
                            const checkbox = document.querySelector(`input[name="httpMethods"][value="${method}"]`);
                            if (checkbox) checkbox.checked = true;
                        });
                    }

                    // 设置令牌桶配置
                    document.getElementById('bucketCapacity').value = rule.bucketCapacity || 10;
                    document.getElementById('refillRate').value = rule.refillRate || 5;
                    document.getElementById('timeWindow').value = rule.timeWindow || 1;

                    // 设置IP限流配置
                    document.getElementById('enableIpLimit').checked = rule.enableIpLimit || false;
                    if (rule.enableIpLimit) {
                        document.getElementById('ipLimitConfig').style.display = 'block';
                        document.getElementById('ipRequestLimit').value = rule.ipRequestLimit || '';
                        document.getElementById('ipBucketCapacity').value = rule.ipBucketCapacity || '';
                    }

                    // 设置用户限流配置
                    document.getElementById('enableUserLimit').checked = rule.enableUserLimit || false;
                    if (rule.enableUserLimit) {
                        document.getElementById('userLimitConfig').style.display = 'block';
                        document.getElementById('userRequestLimit').value = rule.userRequestLimit || '';
                        document.getElementById('userBucketCapacity').value = rule.userBucketCapacity || '';
                    }
                }
            })
            .catch(error => {
                utils.showMessage('加载规则失败: ' + error.message, 'error');
            });
    }
};

// 统计管理功能
const statsManager = {
    // 查看详细统计
    viewDetailed: function(ruleId) {
        currentRuleId = ruleId;
        this.loadDetailedStats(ruleId);
        loadRuleTrendData();
        this.showModal();
    },

    // 加载详细统计数据
    loadDetailedStats: function(ruleId) {
        // 更新模态框标题
        const modalTitle = document.getElementById('modalTitle');
        if (modalTitle) {
            modalTitle.textContent = `详细统计信息 - 规则ID: ${ruleId}`;
        }

        // 加载汇总信息
        this.loadSummaryData(ruleId);

        // 加载IP统计
        api.getIpStats(ruleId, 50)
            .then(response => {
                this.renderIpStatsInModal(response.data);
            })
            .catch(error => {
                console.error('加载IP统计失败:', error);
                const container = document.getElementById('ipDetailContent');
                if (container) {
                    container.innerHTML = '<div class="error">加载IP统计失败: ' + error.message + '</div>';
                }
            });

        // 加载用户统计
        api.getUserStats(ruleId, 50)
            .then(response => {
                this.renderUserStatsInModal(response.data);
            })
            .catch(error => {
                console.error('加载用户统计失败:', error);
                const container = document.getElementById('userDetailContent');
                if (container) {
                    container.innerHTML = '<div class="error">加载用户统计失败: ' + error.message + '</div>';
                }
            });
    },

    // 渲染IP统计
    renderIpStats: function(stats) {
        const container = document.getElementById('ipStatsTable');
        if (!container) return;

        if (stats.length === 0) {
            container.innerHTML = '<div class="no-data">暂无IP统计数据</div>';
            return;
        }

        let html = `
            <table class="table">
                <thead>
                    <tr>
                        <th>IP地址</th>
                        <th>总请求数</th>
                        <th>允许请求</th>
                        <th>阻止请求</th>
                        <th>阻止率</th>
                        <th>最后请求时间</th>
                    </tr>
                </thead>
                <tbody>
        `;

        stats.forEach(stat => {
            html += `
                <tr>
                    <td><code>${stat.dimensionValue}</code></td>
                    <td class="stat-number">${stat.totalRequests}</td>
                    <td class="stat-number success">${stat.allowedRequests}</td>
                    <td class="stat-number danger">${stat.blockedRequests}</td>
                    <td>${utils.formatNumber(stat.blockRate)}%</td>
                    <td>${utils.formatTime(stat.lastRequestTime)}</td>
                </tr>
            `;
        });

        html += '</tbody></table>';
        container.innerHTML = html;
    },

    // 渲染用户统计
    renderUserStats: function(stats) {
        const container = document.getElementById('userStatsTable');
        if (!container) return;

        if (stats.length === 0) {
            container.innerHTML = '<div class="no-data">暂无用户统计数据</div>';
            return;
        }

        let html = `
            <table class="table">
                <thead>
                    <tr>
                        <th>用户ID</th>
                        <th>总请求数</th>
                        <th>允许请求</th>
                        <th>阻止请求</th>
                        <th>阻止率</th>
                        <th>最后请求时间</th>
                    </tr>
                </thead>
                <tbody>
        `;

        stats.forEach(stat => {
            html += `
                <tr>
                    <td><code>${stat.dimensionValue}</code></td>
                    <td class="stat-number">${stat.totalRequests}</td>
                    <td class="stat-number success">${stat.allowedRequests}</td>
                    <td class="stat-number danger">${stat.blockedRequests}</td>
                    <td>${utils.formatNumber(stat.blockRate)}%</td>
                    <td>${utils.formatTime(stat.lastRequestTime)}</td>
                </tr>
            `;
        });

        html += '</tbody></table>';
        container.innerHTML = html;
    },

    // 显示模态框
    showModal: function() {
        if (detailedStatsModal) {
            detailedStatsModal.style.display = 'block';
        }
    },

    // 重置统计
    reset: function(ruleId) {
        if (!utils.confirm('确定要重置这个规则的统计信息吗？')) return;

        api.resetStats(ruleId)
            .then(response => {
                if (response.data.success) {
                    utils.showMessage('统计信息重置成功', 'success');
                    setTimeout(() => location.reload(), 1000);
                } else {
                    utils.showMessage(response.data.message, 'error');
                }
            })
            .catch(error => {
                utils.showMessage('重置失败: ' + error.message, 'error');
            });
    },

    // 加载汇总数据
    loadSummaryData: function(ruleId) {
        const container = document.getElementById('summaryContent');
        if (!container) return;

        container.innerHTML = '<div class="loading">加载汇总信息...</div>';

        // 获取规则的基本统计信息
        api.getStats()
            .then(response => {
                const allStats = response.data;
                const ruleStats = allStats.find(stat => stat.ruleId === ruleId);

                if (ruleStats) {
                    container.innerHTML = `
                        <div class="summary-stats">
                            <div class="summary-card">
                                <h4>基本统计</h4>
                                <div class="stat-grid">
                                    <div class="stat-item">
                                        <span class="stat-label">规则名称:</span>
                                        <span class="stat-value">${ruleStats.ruleName || '未知'}</span>
                                    </div>
                                    <div class="stat-item">
                                        <span class="stat-label">总请求数:</span>
                                        <span class="stat-value">${ruleStats.totalRequests || 0}</span>
                                    </div>
                                    <div class="stat-item">
                                        <span class="stat-label">允许请求:</span>
                                        <span class="stat-value success">${ruleStats.allowedRequests || 0}</span>
                                    </div>
                                    <div class="stat-item">
                                        <span class="stat-label">阻止请求:</span>
                                        <span class="stat-value danger">${ruleStats.blockedRequests || 0}</span>
                                    </div>
                                    <div class="stat-item">
                                        <span class="stat-label">阻止率:</span>
                                        <span class="stat-value warning">${utils.formatNumber(ruleStats.blockRate || 0)}%</span>
                                    </div>
                                    <div class="stat-item">
                                        <span class="stat-label">请求频率:</span>
                                        <span class="stat-value">${utils.formatNumber(ruleStats.requestRate || 0)}/s</span>
                                    </div>
                                    <div class="stat-item">
                                        <span class="stat-label">最后请求:</span>
                                        <span class="stat-value">${utils.formatTime(ruleStats.lastRequestTime)}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                } else {
                    container.innerHTML = '<div class="no-data">未找到该规则的统计信息</div>';
                }
            })
            .catch(error => {
                container.innerHTML = '<div class="error">加载汇总信息失败: ' + error.message + '</div>';
            });
    },

    // 在模态框中渲染IP统计
    renderIpStatsInModal: function(stats) {
        const container = document.getElementById('ipDetailContent');
        if (!container) return;

        if (stats.length === 0) {
            container.innerHTML = '<div class="no-data">暂无IP统计数据</div>';
            return;
        }

        let html = `
            <div class="stats-header">
                <h5>IP维度统计 (显示前${stats.length}个)</h5>
            </div>
            <table class="table">
                <thead>
                    <tr>
                        <th>IP地址</th>
                        <th>总请求数</th>
                        <th>允许请求</th>
                        <th>阻止请求</th>
                        <th>阻止率</th>
                        <th>最后请求时间</th>
                    </tr>
                </thead>
                <tbody>
        `;

        stats.forEach(stat => {
            html += `
                <tr>
                    <td><code>${stat.dimensionValue}</code></td>
                    <td class="stat-number">${stat.totalRequests}</td>
                    <td class="stat-number success">${stat.allowedRequests}</td>
                    <td class="stat-number danger">${stat.blockedRequests}</td>
                    <td>${utils.formatNumber(stat.blockRate)}%</td>
                    <td>${utils.formatTime(stat.lastRequestTime)}</td>
                </tr>
            `;
        });

        html += '</tbody></table>';
        container.innerHTML = html;
    },

    // 在模态框中渲染用户统计
    renderUserStatsInModal: function(stats) {
        const container = document.getElementById('userDetailContent');
        if (!container) return;

        if (stats.length === 0) {
            container.innerHTML = '<div class="no-data">暂无用户统计数据</div>';
            return;
        }

        let html = `
            <div class="stats-header">
                <h5>用户维度统计 (显示前${stats.length}个)</h5>
            </div>
            <table class="table">
                <thead>
                    <tr>
                        <th>用户ID</th>
                        <th>总请求数</th>
                        <th>允许请求</th>
                        <th>阻止请求</th>
                        <th>阻止率</th>
                        <th>最后请求时间</th>
                    </tr>
                </thead>
                <tbody>
        `;

        stats.forEach(stat => {
            html += `
                <tr>
                    <td><code>${stat.dimensionValue}</code></td>
                    <td class="stat-number">${stat.totalRequests}</td>
                    <td class="stat-number success">${stat.allowedRequests}</td>
                    <td class="stat-number danger">${stat.blockedRequests}</td>
                    <td>${utils.formatNumber(stat.blockRate)}%</td>
                    <td>${utils.formatTime(stat.lastRequestTime)}</td>
                </tr>
            `;
        });

        html += '</tbody></table>';
        container.innerHTML = html;
    }
};

// 全局函数（供HTML调用）
function editRule(ruleId) {
    ruleManager.edit(ruleId);
}

function deleteRule(ruleId) {
    ruleManager.delete(ruleId);
}

function toggleRule(ruleId, enabled) {
    ruleManager.toggle(ruleId, enabled);
}

function viewStats(ruleId) {
    window.location.href = `/ratelimit/stats#rule-${ruleId}`;
}

function viewDetailedStats(ruleId) {
    statsManager.viewDetailed(ruleId);
}

function resetStats(ruleId) {
    statsManager.reset(ruleId);
}

function resetAllStats() {
    if (!utils.confirm('确定要重置所有统计信息吗？')) return;

    api.resetAllStats()
        .then(response => {
            if (response.data.success) {
                utils.showMessage('所有统计信息重置成功', 'success');
                setTimeout(() => location.reload(), 1000);
            } else {
                utils.showMessage(response.data.message, 'error');
            }
        })
        .catch(error => {
            utils.showMessage('重置失败: ' + error.message, 'error');
        });
}

function resetRateLimit() {
    if (!utils.confirm('确定要重置所有限流状态吗？')) return;

    api.resetRateLimit()
        .then(response => {
            if (response.data.success) {
                utils.showMessage('限流状态重置成功', 'success');
            } else {
                utils.showMessage(response.data.message, 'error');
            }
        })
        .catch(error => {
            utils.showMessage('重置失败: ' + error.message, 'error');
        });
}

function refreshData() {
    location.reload();
}

function generateTestData() {
    if (!utils.confirm('确定要生成测试数据吗？这将创建一些模拟的统计数据。')) return;

    api.generateTestData()
        .then(response => {
            if (response.data.success) {
                utils.showMessage('测试数据生成成功', 'success');
                setTimeout(() => location.reload(), 1000);
            } else {
                utils.showMessage(response.data.message, 'error');
            }
        })
        .catch(error => {
            utils.showMessage('生成测试数据失败: ' + error.message, 'error');
        });
}

function debugStatus() {
    api.getDebugStatus()
        .then(response => {
            const data = response.data;
            console.log('调试状态:', data);

            let message = `规则数量: ${data.rulesCount || 0}\n`;
            message += `统计数量: ${data.statsCount || 0}\n`;
            message += `全局统计: ${JSON.stringify(data.globalStats || {}, null, 2)}`;

            alert(message);
        })
        .catch(error => {
            utils.showMessage('获取调试状态失败: ' + error.message, 'error');
        });
}

function closeModal() {
    if (detailedStatsModal) {
        detailedStatsModal.style.display = 'none';
    }
}

function showTab(tabName) {
    // 隐藏所有标签内容
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    // 移除所有标签按钮的激活状态
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // 显示选中的标签内容
    const targetTab = document.getElementById(tabName + 'Stats') || document.getElementById(tabName + 'Tab');
    if (targetTab) {
        targetTab.classList.add('active');
    }

    // 激活对应的标签按钮
    event.target.classList.add('active');
}

// 表单相关函数
function resetForm() {
    document.getElementById('ruleForm').reset();
    document.getElementById('ruleId').value = '';
    document.getElementById('ipLimitConfig').style.display = 'none';
    document.getElementById('userLimitConfig').style.display = 'none';
}

function loadRule(ruleId) {
    ruleManager.loadToForm(ruleId);
}

// 表单提交处理
if (document.getElementById('ruleForm')) {
    document.getElementById('ruleForm').addEventListener('submit', function(e) {
        e.preventDefault();

        const formData = new FormData(this);
        const rule = {};

        // 基本字段
        rule.id = formData.get('id') || null;
        rule.name = formData.get('name');
        rule.description = formData.get('description');
        rule.pathPattern = formData.get('pathPattern');
        rule.priority = parseInt(formData.get('priority')) || 100;
        rule.enabled = formData.has('enabled');

        // HTTP方法
        rule.httpMethods = formData.getAll('httpMethods');

        // 令牌桶配置
        rule.bucketCapacity = parseInt(formData.get('bucketCapacity'));
        rule.refillRate = parseInt(formData.get('refillRate'));
        rule.timeWindow = parseInt(formData.get('timeWindow'));

        // IP限流配置
        rule.enableIpLimit = formData.has('enableIpLimit');
        if (rule.enableIpLimit) {
            rule.ipRequestLimit = formData.get('ipRequestLimit') ? parseInt(formData.get('ipRequestLimit')) : null;
            rule.ipBucketCapacity = formData.get('ipBucketCapacity') ? parseInt(formData.get('ipBucketCapacity')) : null;
        }

        // 用户限流配置
        rule.enableUserLimit = formData.has('enableUserLimit');
        if (rule.enableUserLimit) {
            rule.userRequestLimit = formData.get('userRequestLimit') ? parseInt(formData.get('userRequestLimit')) : null;
            rule.userBucketCapacity = formData.get('userBucketCapacity') ? parseInt(formData.get('userBucketCapacity')) : null;
        }

        // 提交数据
        ruleManager.save(rule)
            .then(response => {
                if (response.data.success) {
                    utils.showMessage('规则保存成功', 'success');
                    setTimeout(() => {
                        window.location.href = '/ratelimit/';
                    }, 1000);
                } else {
                    utils.showMessage(response.data.message, 'error');
                }
            })
            .catch(error => {
                utils.showMessage('保存失败: ' + error.message, 'error');
            });
    });
}

// 测试功能
function testRule() {
    const pathPattern = document.getElementById('pathPattern').value;
    const testPath = document.getElementById('testPath').value || '/api/test';

    if (!pathPattern) {
        utils.showMessage('请先填写路径模式', 'warning');
        return;
    }

    // 简单的Ant路径匹配测试
    const result = antPathMatch(pathPattern, testPath);
    const resultDiv = document.getElementById('testResult');

    if (result) {
        resultDiv.className = 'test-result success';
        resultDiv.textContent = `✅ 路径 "${testPath}" 匹配模式 "${pathPattern}"`;
    } else {
        resultDiv.className = 'test-result error';
        resultDiv.textContent = `❌ 路径 "${testPath}" 不匹配模式 "${pathPattern}"`;
    }
}

function simulateRequest() {
    const testPath = document.getElementById('testPath').value || '/api/test';
    const testMethod = document.getElementById('testMethod').value || 'GET';
    const testIp = document.getElementById('testIp').value || '192.168.1.1';
    const testUserId = document.getElementById('testUserId').value || '';

    const params = new URLSearchParams();
    if (testUserId) {
        params.append('userId', testUserId);
    }

    const url = `/test${testPath.startsWith('/') ? '' : '/'}${testPath}${params.toString() ? '?' + params.toString() : ''}`;

    const headers = {
        'X-Forwarded-For': testIp
    };

    if (testUserId) {
        headers['X-User-Id'] = testUserId;
    }

    axios({
        method: testMethod.toLowerCase(),
        url: url,
        headers: headers
    })
    .then(response => {
        const resultDiv = document.getElementById('testResult');
        resultDiv.className = 'test-result success';
        resultDiv.textContent = `✅ 请求成功\n${JSON.stringify(response.data, null, 2)}`;
    })
    .catch(error => {
        const resultDiv = document.getElementById('testResult');
        resultDiv.className = 'test-result error';
        if (error.response && error.response.status === 429) {
            resultDiv.textContent = `🚫 请求被限流\n${JSON.stringify(error.response.data, null, 2)}`;
        } else {
            resultDiv.textContent = `❌ 请求失败: ${error.message}`;
        }
    });
}

function batchTest() {
    const testPath = document.getElementById('testPath').value || '/api/test';
    const testMethod = document.getElementById('testMethod').value || 'GET';
    const testIp = document.getElementById('testIp').value || '192.168.1.1';
    const testUserId = document.getElementById('testUserId').value || '';

    const resultDiv = document.getElementById('testResult');
    resultDiv.className = 'test-result';
    resultDiv.textContent = '正在进行批量测试...';

    const requests = [];
    const batchSize = 10;

    for (let i = 0; i < batchSize; i++) {
        const params = new URLSearchParams();
        if (testUserId) {
            params.append('userId', testUserId);
        }

        const url = `/test${testPath.startsWith('/') ? '' : '/'}${testPath}${params.toString() ? '?' + params.toString() : ''}`;

        const headers = {
            'X-Forwarded-For': testIp
        };

        if (testUserId) {
            headers['X-User-Id'] = testUserId;
        }

        requests.push(
            axios({
                method: testMethod.toLowerCase(),
                url: url,
                headers: headers
            })
            .then(response => ({ success: true, data: response.data }))
            .catch(error => ({
                success: false,
                status: error.response ? error.response.status : 0,
                data: error.response ? error.response.data : { message: error.message }
            }))
        );
    }

    Promise.all(requests)
        .then(results => {
            const successCount = results.filter(r => r.success).length;
            const blockedCount = results.filter(r => !r.success && r.status === 429).length;
            const errorCount = results.filter(r => !r.success && r.status !== 429).length;

            resultDiv.className = 'test-result success';
            resultDiv.textContent = `批量测试完成 (${batchSize}个请求):\n✅ 成功: ${successCount}\n🚫 被限流: ${blockedCount}\n❌ 错误: ${errorCount}`;
        });
}

// 简单的Ant路径匹配实现
function antPathMatch(pattern, path) {
    if (!pattern || !path) return false;

    // 转换Ant模式为正则表达式
    let regex = pattern
        .replace(/\*\*/g, '.*')  // ** 匹配任意路径
        .replace(/\*/g, '[^/]*') // * 匹配除/外的任意字符
        .replace(/\?/g, '.');    // ? 匹配单个字符

    regex = '^' + regex + '$';

    try {
        return new RegExp(regex).test(path);
    } catch (e) {
        return false;
    }
}

// 统计页面功能
function refreshStats() {
    // 刷新趋势图
    if (typeof loadTrendData === 'function') {
        loadTrendData();
    }

    // 刷新维度概览
    if (typeof loadDimensionOverview === 'function') {
        loadDimensionOverview();
    }

    // 如果需要完全刷新，可以取消注释下面的行
    // location.reload();

    utils.showMessage('统计数据已刷新', 'success');
}

function exportStats() {
    api.getStats()
        .then(response => {
            const stats = response.data;
            const csv = convertToCSV(stats);
            downloadCSV(csv, 'ratelimit-stats.csv');
            utils.showMessage('统计数据导出成功', 'success');
        })
        .catch(error => {
            utils.showMessage('导出失败: ' + error.message, 'error');
        });
}

function convertToCSV(data) {
    if (!data || data.length === 0) return '';

    const headers = ['规则ID', '规则名称', '总请求数', '允许请求', '阻止请求', '阻止率', '请求频率'];
    const rows = data.map(item => [
        item.ruleId,
        item.ruleName,
        item.totalRequests,
        item.allowedRequests,
        item.blockedRequests,
        utils.formatNumber(item.blockRate),
        utils.formatNumber(item.requestRate)
    ]);

    return [headers, ...rows].map(row => row.join(',')).join('\n');
}

function downloadCSV(csv, filename) {
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', filename);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function changeTimeRange() {
    const timeRange = document.getElementById('timeRange');
    if (!timeRange) {
        console.warn('找不到时间范围选择器');
        return;
    }

    const minutes = parseInt(timeRange.value);
    const selectedText = timeRange.options[timeRange.selectedIndex].text;

    // 重新加载趋势数据
    loadTrendData();
    utils.showMessage(`时间范围已切换: ${selectedText}`, 'info');
}


function changeRuleTimeRange() {
    const timeRange = document.getElementById('ruleTimeRange');
    if (!timeRange) {
        console.warn('找不到时间范围选择器');
        return;
    }

    const minutes = parseInt(timeRange.value);
    const selectedText = timeRange.options[timeRange.selectedIndex].text;

    // 重新加载趋势数据
    loadRuleTrendData();
    utils.showMessage(`时间范围已切换: ${selectedText}`, 'info');
}

function showDimensionTab(tabName) {
    // 隐藏所有标签内容
    document.querySelectorAll('#overviewTab, #ipTab, #userTab, #recordsTab').forEach(tab => {
        tab.classList.remove('active');
    });

    // 移除所有标签按钮的激活状态
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // 显示选中的标签内容
    const targetTab = document.getElementById(tabName + 'Tab');
    if (targetTab) {
        targetTab.classList.add('active');
    }

    // 激活对应的标签按钮
    event.target.classList.add('active');

    // 加载对应的数据
    if (tabName === 'overview') {
        loadDimensionOverview();
    } else if (tabName === 'records') {
        // 显示/隐藏时间范围控件
        const minutesInput = document.getElementById('recordsMinutes');
        const minutesLabel = document.querySelector('label[for="recordsMinutes"]');
        if (minutesInput && minutesLabel) {
            minutesInput.style.display = 'none';
            minutesLabel.style.display = 'none';
        }
    }
}

function loadDimensionOverview() {
    // 加载IP和用户维度的概览数据
    const ipOverview = document.getElementById('ipOverview');
    const userOverview = document.getElementById('userOverview');

    if (ipOverview) {
        ipOverview.innerHTML = '<div class="loading">加载IP概览数据...</div>';
    }

    if (userOverview) {
        userOverview.innerHTML = '<div class="loading">加载用户概览数据...</div>';
    }

    // 调用API获取真实的概览数据
    api.getDimensionOverview()
        .then(response => {
            const data = response.data;

            if (ipOverview && data.ip) {
                ipOverview.innerHTML = `
                    <div class="overview-stats">
                        <div class="stat-item">
                            <span class="stat-label">总IP数:</span>
                            <span class="stat-value">${data.ip.totalCount || 0}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">活跃IP数:</span>
                            <span class="stat-value success">${data.ip.activeCount || 0}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">被限流IP数:</span>
                            <span class="stat-value danger">${data.ip.blockedCount || 0}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">限流率:</span>
                            <span class="stat-value warning">${data.ip.totalCount > 0 ? ((data.ip.blockedCount / data.ip.totalCount) * 100).toFixed(1) : 0}%</span>
                        </div>
                    </div>
                `;
            }

            if (userOverview && data.user) {
                userOverview.innerHTML = `
                    <div class="overview-stats">
                        <div class="stat-item">
                            <span class="stat-label">总用户数:</span>
                            <span class="stat-value">${data.user.totalCount || 0}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">活跃用户数:</span>
                            <span class="stat-value success">${data.user.activeCount || 0}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">被限流用户数:</span>
                            <span class="stat-value danger">${data.user.blockedCount || 0}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">限流率:</span>
                            <span class="stat-value warning">${data.user.totalCount > 0 ? ((data.user.blockedCount / data.user.totalCount) * 100).toFixed(1) : 0}%</span>
                        </div>
                    </div>
                `;
            }
        })
        .catch(error => {
            console.error('加载维度概览数据失败:', error);

            if (ipOverview) {
                ipOverview.innerHTML = '<div class="error">加载IP概览数据失败</div>';
            }

            if (userOverview) {
                userOverview.innerHTML = '<div class="error">加载用户概览数据失败</div>';
            }
        });
}

function loadIpStats() {
    const ruleId = document.getElementById('ipRuleSelect').value;
    const limit = document.getElementById('ipLimit').value || 20;
    const container = document.getElementById('ipStatsContent');

    if (!ruleId) {
        container.innerHTML = '<div class="no-data">请选择规则查看IP统计</div>';
        return;
    }

    container.innerHTML = '<div class="loading">加载IP统计数据...</div>';

    api.getIpStats(ruleId, limit)
        .then(response => {
            statsManager.renderIpStatsInContainer(response.data, container);
        })
        .catch(error => {
            container.innerHTML = '<div class="error">加载失败: ' + error.message + '</div>';
        });
}

function loadUserStats() {
    const ruleId = document.getElementById('userRuleSelect').value;
    const limit = document.getElementById('userLimit').value || 20;
    const container = document.getElementById('userStatsContent');

    if (!ruleId) {
        container.innerHTML = '<div class="no-data">请选择规则查看用户统计</div>';
        return;
    }

    container.innerHTML = '<div class="loading">加载用户统计数据...</div>';

    api.getUserStats(ruleId, limit)
        .then(response => {
            statsManager.renderUserStatsInContainer(response.data, container);
        })
        .catch(error => {
            container.innerHTML = '<div class="error">加载失败: ' + error.message + '</div>';
        });
}

function resetRuleStats(ruleId) {
    resetStats(ruleId);
}

function loadRateLimitRecords() {
    const ruleSelect = document.getElementById('recordsRuleSelect');
    const limit = document.getElementById('recordsLimit').value || 50;
    const container = document.getElementById('recordsContent');
    const minutesInput = document.getElementById('recordsMinutes');
    const minutesLabel = document.querySelector('label[for="recordsMinutes"]');

    if (!ruleSelect.value) {
        container.innerHTML = '<div class="no-data">请选择规则查看限流记录</div>';
        return;
    }

    container.innerHTML = '<div class="loading">加载限流记录...</div>';

    if (ruleSelect.value === 'recent') {
        // 显示时间范围控件
        if (minutesInput && minutesLabel) {
            minutesInput.style.display = 'inline-block';
            minutesLabel.style.display = 'inline-block';
        }

        const minutes = document.getElementById('recordsMinutes').value || 60;
        api.getRecentRateLimitRecords(minutes, limit)
            .then(response => {
                renderRateLimitRecords(response.data, container);
            })
            .catch(error => {
                container.innerHTML = '<div class="error">加载失败: ' + error.message + '</div>';
            });
    } else {
        // 隐藏时间范围控件
        if (minutesInput && minutesLabel) {
            minutesInput.style.display = 'none';
            minutesLabel.style.display = 'none';
        }

        api.getRateLimitRecords(ruleSelect.value, limit)
            .then(response => {
                renderRateLimitRecords(response.data, container);
            })
            .catch(error => {
                container.innerHTML = '<div class="error">加载失败: ' + error.message + '</div>';
            });
    }
}

function renderRateLimitRecords(records, container) {
    if (records.length === 0) {
        container.innerHTML = '<div class="no-data">暂无限流记录</div>';
        return;
    }

    let html = `
        <table class="table">
            <thead>
                <tr>
                    <th>时间</th>
                    <th>规则</th>
                    <th>路径</th>
                    <th>方法</th>
                    <th>IP地址</th>
                    <th>用户ID</th>
                    <th>状态</th>
                    <th>阻止原因</th>
                    <th>User-Agent</th>
                </tr>
            </thead>
            <tbody>
    `;

    records.forEach(record => {
        const statusClass = record.blocked ? 'danger' : 'success';
        const statusText = record.blocked ? '🚫 被阻止' : '✅ 通过';
        const userAgent = record.userAgent ?
            (record.userAgent.length > 50 ? record.userAgent.substring(0, 50) + '...' : record.userAgent) :
            'unknown';

        html += `
            <tr>
                <td>${utils.formatTime(record.requestTime)}</td>
                <td>${record.ruleName}</td>
                <td><code>${record.requestPath}</code></td>
                <td><span class="method-tag">${record.httpMethod}</span></td>
                <td><code>${record.clientIp || 'unknown'}</code></td>
                <td><code>${record.userId || 'anonymous'}</code></td>
                <td><span class="${statusClass}">${statusText}</span></td>
                <td>${record.blockReason || '-'}</td>
                <td title="${record.userAgent || 'unknown'}">${userAgent}</td>
            </tr>
        `;
    });

    html += '</tbody></table>';
    container.innerHTML = html;
}

function showModalTab(tabName) {
    // 隐藏所有模态框标签内容
    document.querySelectorAll('#summaryTab, #ipDetailTab, #userDetailTab').forEach(tab => {
        tab.classList.remove('active');
    });

    // 移除所有模态框标签按钮的激活状态
    document.querySelectorAll('.modal-tabs .tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // 显示选中的标签内容
    const targetTab = document.getElementById(tabName + 'Tab') || document.getElementById(tabName + 'DetailTab');
    if (targetTab) {
        targetTab.classList.add('active');
    } else {
        console.warn('找不到标签页:', tabName);
    }

    // 激活对应的标签按钮
    if (event && event.target) {
        event.target.classList.add('active');
    }
}

// 扩展statsManager
statsManager.renderIpStatsInContainer = function(stats, container) {
    if (stats.length === 0) {
        container.innerHTML = '<div class="no-data">暂无IP统计数据</div>';
        return;
    }

    let html = `
        <table class="table">
            <thead>
                <tr>
                    <th>IP地址</th>
                    <th>总请求数</th>
                    <th>允许请求</th>
                    <th>阻止请求</th>
                    <th>阻止率</th>
                    <th>最后请求时间</th>
                </tr>
            </thead>
            <tbody>
    `;

    stats.forEach(stat => {
        html += `
            <tr>
                <td><code>${stat.dimensionValue}</code></td>
                <td class="stat-number">${stat.totalRequests}</td>
                <td class="stat-number success">${stat.allowedRequests}</td>
                <td class="stat-number danger">${stat.blockedRequests}</td>
                <td>${utils.formatNumber(stat.blockRate)}%</td>
                <td>${utils.formatTime(stat.lastRequestTime)}</td>
            </tr>
        `;
    });

    html += '</tbody></table>';
    container.innerHTML = html;
};

statsManager.renderUserStatsInContainer = function(stats, container) {
    if (stats.length === 0) {
        container.innerHTML = '<div class="no-data">暂无用户统计数据</div>';
        return;
    }

    let html = `
        <table class="table">
            <thead>
                <tr>
                    <th>用户ID</th>
                    <th>总请求数</th>
                    <th>允许请求</th>
                    <th>阻止请求</th>
                    <th>阻止率</th>
                    <th>最后请求时间</th>
                </tr>
            </thead>
            <tbody>
    `;

    stats.forEach(stat => {
        html += `
            <tr>
                <td><code>${stat.dimensionValue}</code></td>
                <td class="stat-number">${stat.totalRequests}</td>
                <td class="stat-number success">${stat.allowedRequests}</td>
                <td class="stat-number danger">${stat.blockedRequests}</td>
                <td>${utils.formatNumber(stat.blockRate)}%</td>
                <td>${utils.formatTime(stat.lastRequestTime)}</td>
            </tr>
        `;
    });

    html += '</tbody></table>';
    container.innerHTML = html;
};

// 图表相关功能
function loadTrendData() {
    // 检查图表是否已初始化
    if (!window.requestTrendChart) {
        console.warn('图表尚未初始化，跳过数据加载');
        return;
    }

    const timeRange = document.getElementById('timeRange');
    const minutes = timeRange ? parseInt(timeRange.value) : 60;

    api.getTrendData(minutes)
        .then(response => {
            const data = response.data;

            if (window.requestTrendChart) {
                window.requestTrendChart.data.labels = data.labels || [];
                window.requestTrendChart.data.datasets[0].data = data.totalData || [];
                window.requestTrendChart.data.datasets[1].data = data.blockedData || [];
                window.requestTrendChart.update();

                // 显示数据间隔信息
                const intervalInfo = getIntervalInfo(data.intervalMinutes, data.dataPoints);
                console.log(`趋势图已更新: ${intervalInfo}`);

                // 更新页面上的信息显示
                const chartInfoElement = document.getElementById('chartInfo');
                if (chartInfoElement) {
                    chartInfoElement.textContent = `显示 ${intervalInfo}`;
                }
            }
        })
        .catch(error => {
            console.error('加载趋势数据失败:', error);

            // 如果加载失败，显示空数据
            if (window.requestTrendChart) {
                window.requestTrendChart.data.labels = [];
                window.requestTrendChart.data.datasets[0].data = [];
                window.requestTrendChart.data.datasets[1].data = [];
                window.requestTrendChart.update();
            }
        });
}


function loadRuleTrendData(ruleId=currentRuleId) {



    const timeRange = document.getElementById('ruleTimeRange');
    const minutes = timeRange ? parseInt(timeRange.value) : 60;

    api.getRuleTrendData(minutes,ruleId)
        .then(response => {
            const data = response.data;

            if (window.detailTrendChart) {
                window.detailTrendChart.data.labels = data.labels || [];
                window.detailTrendChart.data.datasets[0].data = data.totalData || [];
                window.detailTrendChart.data.datasets[1].data = data.blockedData || [];
                window.detailTrendChart.update();

                // 显示数据间隔信息
                const intervalInfo = getIntervalInfo(data.intervalMinutes, data.dataPoints);
                console.log(`趋势图已更新: ${intervalInfo}`);

                // 更新页面上的信息显示
                const chartInfoElement = document.getElementById('chartInfo');
                if (chartInfoElement) {
                    chartInfoElement.textContent = `显示 ${intervalInfo}`;
                }
            }
        })
        .catch(error => {
            console.error('加载趋势数据失败:', error);

            // 如果加载失败，显示空数据
            if (window.detailTrendChart) {
                window.detailTrendChart.data.labels = [];
                window.detailTrendChart.data.datasets[0].data = [];
                window.detailTrendChart.data.datasets[1].data = [];
                window.detailTrendChart.update();
            }
        });
}

// 调试函数 - 检查图表状态
function checkChartStatus() {
    console.log('Chart.js 是否加载:', typeof Chart !== 'undefined');
    console.log('requestTrendChart 是否存在:', !!window.requestTrendChart);
    console.log('图表画布是否存在:', !!document.getElementById('requestTrendChart'));

    if (window.requestTrendChart) {
        console.log('图表数据:', window.requestTrendChart.data);
    }
}

// 手动重新初始化图表
function reinitChart() {
    if (window.requestTrendChart) {
        window.requestTrendChart.destroy();
        window.requestTrendChart = null;
    }

    // 重新初始化
    if (typeof initCharts === 'function') {
        initCharts();
    } else {
        console.error('initCharts 函数不存在');
    }
}

// 获取间隔信息描述
function getIntervalInfo(intervalMinutes, dataPoints) {
    if (!intervalMinutes || !dataPoints) {
        return '无数据';
    }

    let intervalDesc;
    if (intervalMinutes === 1) {
        intervalDesc = '每分钟';
    } else if (intervalMinutes < 60) {
        intervalDesc = `每${intervalMinutes}分钟`;
    } else {
        intervalDesc = `每${intervalMinutes / 60}小时`;
    }

    return `${dataPoints}个数据点，${intervalDesc}`;
}
