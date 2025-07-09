<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>限流规则配置</title>
    <link href="/css/ratelimit.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
</head>
<body>
    <div class="container">
        <header class="header">
            <h1>限流管理系统</h1>
            <nav class="nav">
                <a href="/ratelimit/" class="nav-link">首页</a>
                <a href="/ratelimit/config" class="nav-link active">配置管理</a>
                <a href="/ratelimit/stats" class="nav-link">统计分析</a>
            </nav>
        </header>

        <main class="main">
            <div class="section">
                <h2>新增/编辑限流规则</h2>
                <form id="ruleForm" class="form">
                    <input type="hidden" id="ruleId" name="id">

                    <!-- 基本信息 -->
                    <div class="form-section">
                        <h3>基本信息</h3>
                        <div class="form-row">
                            <div class="form-group">
                                <label for="name">规则名称 *</label>
                                <input type="text" id="name" name="name" required>
                            </div>
                            <div class="form-group">
                                <label for="priority">优先级</label>
                                <input type="number" id="priority" name="priority" value="100" min="1" max="1000">
                                <small>数字越小优先级越高</small>
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="description">规则描述</label>
                            <textarea id="description" name="description" rows="3"></textarea>
                        </div>
                        <div class="form-group">
                            <label>
                                <input type="checkbox" id="enabled" name="enabled" checked>
                                启用规则
                            </label>
                        </div>
                    </div>

                    <!-- 路径和方法配置 -->
                    <div class="form-section">
                        <h3>路径和方法配置</h3>
                        <div class="form-group">
                            <label for="pathPattern">路径模式 * (支持Ant风格)</label>
                            <input type="text" id="pathPattern" name="pathPattern" required
                                   placeholder="例如: /api/**, /user/*, /test">
                            <small>支持通配符: ? 匹配单个字符, * 匹配任意字符, ** 匹配任意路径</small>
                        </div>
                        <div class="form-group">
                            <label>HTTP方法 (不选择表示所有方法)</label>
                            <div class="checkbox-group">
                                <#list httpMethods as method>
                                <label class="checkbox-label">
                                    <input type="checkbox" name="httpMethods" value="${method.name()}">
                                    ${method.method}
                                </label>
                                </#list>
                            </div>
                        </div>
                    </div>

                    <!-- 限流说明 -->
                    <div class="form-section">
                        <h3>限流说明</h3>
                        <div class="form-group">
                            <div class="info-box">
                                <p><strong>默认限流：</strong>所有规则都会基于路径进行限流</p>
                                <p><strong>IP维度限流：</strong>可选择启用，对每个IP地址单独限流</p>
                                <p><strong>用户维度限流：</strong>可选择启用，对每个用户ID单独限流</p>
                                <p><strong>多维度校验：</strong>如果启用多个维度，请求需要通过所有维度的限流检查</p>
                            </div>
                        </div>
                    </div>

                    <!-- 令牌桶配置 -->
                    <div class="form-section">
                        <h3>令牌桶配置</h3>
                        <div class="form-row">
                            <div class="form-group">
                                <label for="bucketCapacity">令牌桶容量 *</label>
                                <input type="number" id="bucketCapacity" name="bucketCapacity" required min="1" value="10">
                                <small>突发请求数，建议设置为平均请求量的2-5倍</small>
                            </div>
                            <div class="form-group">
                                <label for="refillRate">令牌补充速率 *</label>
                                <input type="number" id="refillRate" name="refillRate" required min="1" value="5">
                                <small>每秒补充的令牌数</small>
                            </div>
                            <div class="form-group">
                                <label for="timeWindow">时间窗口 *</label>
                                <input type="number" id="timeWindow" name="timeWindow" required min="1" value="1">
                                <small>时间窗口（秒）</small>
                            </div>
                        </div>
                    </div>

                    <!-- IP限流配置 -->
                    <div class="form-section">
                        <h3>IP维度限流配置</h3>
                        <div class="form-group">
                            <label>
                                <input type="checkbox" id="enableIpLimit" name="enableIpLimit">
                                启用IP维度限流
                            </label>
                        </div>
                        <div id="ipLimitConfig" class="sub-config" style="display: none;">
                            <div class="form-row">
                                <div class="form-group">
                                    <label for="ipRequestLimit">IP请求限制</label>
                                    <input type="number" id="ipRequestLimit" name="ipRequestLimit" min="1">
                                    <small>每个IP的请求限制，不填则使用令牌补充速率</small>
                                </div>
                                <div class="form-group">
                                    <label for="ipBucketCapacity">IP令牌桶容量</label>
                                    <input type="number" id="ipBucketCapacity" name="ipBucketCapacity" min="1">
                                    <small>每个IP的令牌桶容量，不填则使用全局配置</small>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 用户限流配置 -->
                    <div class="form-section">
                        <h3>用户维度限流配置</h3>
                        <div class="form-group">
                            <label>
                                <input type="checkbox" id="enableUserLimit" name="enableUserLimit">
                                启用用户维度限流
                            </label>
                        </div>
                        <div id="userLimitConfig" class="sub-config" style="display: none;">
                            <div class="form-row">
                                <div class="form-group">
                                    <label for="userRequestLimit">用户请求限制</label>
                                    <input type="number" id="userRequestLimit" name="userRequestLimit" min="1">
                                    <small>每个用户的请求限制，不填则使用令牌补充速率</small>
                                </div>
                                <div class="form-group">
                                    <label for="userBucketCapacity">用户令牌桶容量</label>
                                    <input type="number" id="userBucketCapacity" name="userBucketCapacity" min="1">
                                    <small>每个用户的令牌桶容量，不填则使用全局配置</small>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 提交按钮 -->
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">保存规则</button>
                        <button type="button" class="btn btn-secondary" onclick="resetForm()">重置</button>
                        <button type="button" class="btn btn-info" onclick="testRule()">测试规则</button>
                        <a href="/ratelimit/" class="btn btn-default">返回</a>
                    </div>
                </form>
            </div>

            <!-- 测试区域 -->
            <div class="section">
                <h2>规则测试</h2>
                <div class="test-area">
                    <div class="form-row">
                        <div class="form-group">
                            <label for="testPath">测试路径</label>
                            <input type="text" id="testPath" placeholder="/api/test">
                        </div>
                        <div class="form-group">
                            <label for="testMethod">HTTP方法</label>
                            <select id="testMethod">
                                <option value="GET">GET</option>
                                <option value="POST">POST</option>
                                <option value="PUT">PUT</option>
                                <option value="DELETE">DELETE</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="testIp">测试IP</label>
                            <input type="text" id="testIp" placeholder="192.168.1.1">
                        </div>
                        <div class="form-group">
                            <label for="testUserId">用户ID</label>
                            <input type="text" id="testUserId" placeholder="user123">
                        </div>
                    </div>
                    <div class="form-actions">
                        <button type="button" class="btn btn-info" onclick="simulateRequest()">模拟请求</button>
                        <button type="button" class="btn btn-warning" onclick="batchTest()">批量测试</button>
                    </div>
                    <div id="testResult" class="test-result"></div>
                </div>
            </div>
        </main>
    </div>

    <script>
        // 页面加载完成后的初始化
        document.addEventListener('DOMContentLoaded', function() {
            // 绑定IP限流开关事件
            document.getElementById('enableIpLimit').addEventListener('change', function() {
                const config = document.getElementById('ipLimitConfig');
                config.style.display = this.checked ? 'block' : 'none';
            });

            // 绑定用户限流开关事件
            document.getElementById('enableUserLimit').addEventListener('change', function() {
                const config = document.getElementById('userLimitConfig');
                config.style.display = this.checked ? 'block' : 'none';
            });

            // 检查是否是编辑模式
            const urlParams = new URLSearchParams(window.location.search);
            const ruleId = urlParams.get('id');
            if (ruleId) {
                loadRule(ruleId);
            }
        });
    </script>
    <script src="/js/ratelimit.js"></script>
</body>
</html>
