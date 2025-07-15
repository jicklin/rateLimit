<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>防重复提交测试 - 限流管理</title>
    <link href="/css/ratelimit.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <style>
        .test-section {
            margin-bottom: 30px;
            padding: 20px;
            border: 1px solid #ddd;
            border-radius: 5px;
        }
        .test-button {
            margin: 5px;
            padding: 10px 20px;
            background-color: #007bff;
            color: white;
            border: none;
            border-radius: 3px;
            cursor: pointer;
        }
        .test-button:hover {
            background-color: #0056b3;
        }
        .test-button:disabled {
            background-color: #6c757d;
            cursor: not-allowed;
        }
        .result-area {
            margin-top: 15px;
            padding: 10px;
            background-color: #f8f9fa;
            border-radius: 3px;
            white-space: pre-wrap;
            font-family: monospace;
            max-height: 200px;
            overflow-y: auto;
        }
        .success {
            color: #28a745;
        }
        .error {
            color: #dc3545;
        }
        .info {
            color: #17a2b8;
        }
    </style>
</head>
<body>
    <div class="container">
        <header class="header">
            <h1>限流管理系统</h1>
            <nav class="nav">
                <a href="/ratelimit/" class="nav-link">首页</a>
                <a href="/ratelimit/config" class="nav-link">配置管理</a>
                <a href="/ratelimit/stats" class="nav-link">统计分析</a>
                <a href="/ratelimit/optimized-stats" class="nav-link">优化统计</a>
                <a href="/ratelimit/duplicate-submit-test" class="nav-link active">防重复提交测试</a>
            </nav>
        </header>

        <main class="main">
            <h2>防重复提交功能测试</h2>

            <div class="section">
                <h3>测试说明</h3>
                <p>基于Redis SETNX + Lua脚本实现的防重复提交功能测试。使用原子性操作避免竞态条件，保证高并发场景下的一致性。</p>
                <p><strong>技术特点</strong>：简化锁值管理，移除ThreadLocal复杂性，直接在AOP中管理锁的生成和删除，实现更安全、更简洁的防重复提交机制。</p>
                <p>请快速连续点击同一个按钮来测试防重复效果。</p>
            </div>

            <!-- 基础测试 -->
            <div class="test-section">
                <h4>1. 基础测试（5秒防重复）</h4>
                <p>默认配置：5秒内防止重复提交</p>
                <button class="test-button" onclick="testBasic()">提交测试</button>
                <div id="basic-result" class="result-area"></div>
            </div>

            <!-- 自定义间隔测试 -->
            <div class="test-section">
                <h4>2. 自定义间隔测试（10秒防重复）</h4>
                <p>自定义时间间隔：10秒内防止重复提交</p>
                <button class="test-button" onclick="testCustomInterval()">提交测试</button>
                <div id="custom-interval-result" class="result-area"></div>
            </div>

            <!-- 只包含标注参数测试 -->
            <div class="test-section">
                <h4>3. 只包含标注参数测试（5秒防重复）</h4>
                <p>INCLUDE_ANNOTATED策略：只包含被@DuplicateSubmitParam标注的参数</p>
                <button class="test-button" onclick="testIncludeAnnotatedParams()">提交测试</button>
                <div id="include-annotated-result" class="result-area"></div>
            </div>

            <!-- 排除标注参数测试 -->
            <div class="test-section">
                <h4>4. 排除标注参数测试（5秒防重复）</h4>
                <p>EXCLUDE_ANNOTATED策略：排除被@DuplicateSubmitIgnore标注的参数</p>
                <button class="test-button" onclick="testExcludeAnnotatedParams()">提交测试</button>
                <div id="exclude-annotated-result" class="result-area"></div>
            </div>

            <!-- 对象属性提取测试 -->
            <div class="test-section">
                <h4>5. 对象属性提取测试（5秒防重复）</h4>
                <p>支持通过path路径提取对象属性，如user.id, order.orderId</p>
                <button class="test-button" onclick="testObjectPathExtraction()">提交测试</button>
                <div id="object-path-result" class="result-area"></div>
            </div>

            <!-- 全局限制测试 -->
            <div class="test-section">
                <h4>6. 全局限制测试（15秒防重复）</h4>
                <p>所有用户共享限制：15秒内只能有一个请求</p>
                <button class="test-button" onclick="testGlobal()">提交测试</button>
                <div id="global-result" class="result-area"></div>
            </div>

            <!-- 排除所有参数测试 -->
            <div class="test-section">
                <h4>7. 排除所有参数测试（8秒防重复）</h4>
                <p>EXCLUDE_ALL策略：同一用户8秒内只能访问一次，不管参数是什么</p>
                <button class="test-button" onclick="testExcludeAllParams()">提交测试</button>
                <div id="exclude-all-result" class="result-area"></div>
            </div>

            <!-- 自定义前缀测试 -->
            <div class="test-section">
                <h4>8. 自定义前缀测试（6秒防重复）</h4>
                <p>使用order前缀，6秒内防止重复提交</p>
                <button class="test-button" onclick="testCustomPrefix()">提交测试</button>
                <div id="custom-prefix-result" class="result-area"></div>
            </div>

            <!-- GET请求测试 -->
            <div class="test-section">
                <h4>9. GET请求测试（3秒防重复）</h4>
                <p>GET请求也支持防重复提交</p>
                <button class="test-button" onclick="testGet()">GET测试</button>
                <div id="get-result" class="result-area"></div>
            </div>

            <!-- 长时间处理测试 -->
            <div class="test-section">
                <h4>10. 长时间处理测试（30秒防重复）</h4>
                <p>模拟长时间处理的接口，30秒防重复</p>
                <button class="test-button" onclick="testLongProcess()">提交测试</button>
                <div id="long-process-result" class="result-area"></div>
            </div>

            <!-- 批量测试 -->
            <div class="test-section">
                <h4>11. 批量测试</h4>
                <p>快速发送多个请求测试防重复效果</p>
                <button class="test-button" onclick="batchTest()">批量测试（5次）</button>
                <div id="batch-result" class="result-area"></div>
            </div>

            <!-- 并发测试 -->
            <div class="test-section">
                <h4>12. 并发测试（2秒防重复）</h4>
                <p>测试高并发场景下的防重复效果</p>
                <button class="test-button" onclick="testConcurrent()">并发测试</button>
                <div id="concurrent-result" class="result-area"></div>
            </div>

            <!-- SETNX原子性测试 -->
            <div class="test-section">
                <h4>13. SETNX原子性测试（3秒防重复）</h4>
                <p>验证Redis SETNX + Lua脚本的原子性操作</p>
                <button class="test-button" onclick="testSetnx()">SETNX测试</button>
                <div id="setnx-result" class="result-area"></div>
            </div>

            <!-- 锁释放测试 -->
            <div class="test-section">
                <h4>14. 锁释放测试</h4>
                <p>验证处理完成后主动释放锁的机制</p>
                <button class="test-button" onclick="testLockReleaseFast()">快速处理测试（10秒防重复，1秒处理）</button>
                <button class="test-button" onclick="testLockReleaseSlow()">长时间处理测试（5秒防重复，3秒处理）</button>
                <div id="lock-release-result" class="result-area"></div>
            </div>

            <!-- Key生成优化测试 -->
            <div class="test-section">
                <h4>15. Key生成优化测试（3秒防重复）</h4>
                <p>验证Key只生成一次的优化效果，减少66.7%的重复计算</p>
                <button class="test-button" onclick="testKeyGenerationOptimization()">Key优化测试</button>
                <div id="key-optimization-result" class="result-area"></div>
            </div>
        </main>
    </div>

    <script>
        // 基础测试
        function testBasic() {
            const data = {
                message: "基础测试",
                timestamp: Date.now(),
                random: Math.random()
            };

            makeRequest('/test/duplicate-submit/basic', data, 'basic-result');
        }

        // 自定义间隔测试
        function testCustomInterval() {
            const data = {
                message: "自定义间隔测试",
                timestamp: Date.now()
            };

            makeRequest('/test/duplicate-submit/custom-interval', data, 'custom-interval-result');
        }

        // 只包含标注参数测试
        function testIncludeAnnotatedParams() {
            const params = new URLSearchParams({
                orderNumber: 'ORD' + Date.now(),
                userCode: 'USER123',
                timestamp: Date.now().toString(),
                requestId: Math.random().toString(36)
            });

            const data = {
                message: "只包含标注参数测试",
                testType: "INCLUDE_ANNOTATED"
            };

            axios.post('/test/duplicate-submit/include-annotated-params?' + params.toString(), data)
                .then(function(response) {
                    displayResult('include-annotated-result', response.data, 'success');
                })
                .catch(function(error) {
                    displayResult('include-annotated-result', error.response?.data || {error: error.message}, 'error');
                });
        }

        // 排除标注参数测试
        function testExcludeAnnotatedParams() {
            const params = new URLSearchParams({
                orderNumber: 'ORD' + Date.now(),
                userCode: 'USER123',
                timestamp: Date.now().toString(),
                requestId: Math.random().toString(36)
            });

            const data = {
                message: "排除标注参数测试",
                testType: "EXCLUDE_ANNOTATED"
            };

            axios.post('/test/duplicate-submit/exclude-annotated-params?' + params.toString(), data)
                .then(function(response) {
                    displayResult('exclude-annotated-result', response.data, 'success');
                })
                .catch(function(error) {
                    displayResult('exclude-annotated-result', error.response?.data || {error: error.message}, 'error');
                });
        }

        // 对象属性提取测试
        function testObjectPathExtraction() {
            const params = new URLSearchParams({
                sessionId: 'SESSION' + Date.now()
            });

            const data = {
                orderId: 'ORD' + Date.now(),
                user: {
                    id: 'USER123',
                    name: 'Test User'
                },
                message: "对象属性提取测试",
                testType: "OBJECT_PATH_EXTRACTION"
            };

            axios.post('/test/duplicate-submit/object-path-extraction?' + params.toString(), data)
                .then(function(response) {
                    displayResult('object-path-result', response.data, 'success');
                })
                .catch(function(error) {
                    displayResult('object-path-result', error.response?.data || {error: error.message}, 'error');
                });
        }

        // 全局限制测试
        function testGlobal() {
            const data = {
                message: "全局限制测试",
                timestamp: Date.now()
            };

            makeRequest('/test/duplicate-submit/global', data, 'global-result');
        }

        // 排除所有参数测试
        function testExcludeAllParams() {
            const params = new URLSearchParams({
                orderNumber: 'ORD' + Date.now(),
                userCode: 'USER123',
                timestamp: Date.now().toString()
            });

            const data = {
                message: "排除所有参数测试",
                randomData: Math.random(), // 这些参数都会被忽略
                testType: "EXCLUDE_ALL"
            };

            axios.post('/test/duplicate-submit/exclude-all-params?' + params.toString(), data)
                .then(function(response) {
                    displayResult('exclude-all-result', response.data, 'success');
                })
                .catch(function(error) {
                    displayResult('exclude-all-result', error.response?.data || {error: error.message}, 'error');
                });
        }

        // 自定义前缀测试
        function testCustomPrefix() {
            const data = {
                message: "自定义前缀测试",
                orderId: Math.random().toString(36)
            };

            makeRequest('/test/duplicate-submit/custom-prefix', data, 'custom-prefix-result');
        }

        // GET请求测试
        function testGet() {
            const params = new URLSearchParams({
                param1: 'value1',
                param2: Date.now().toString()
            });

            axios.get('/test/duplicate-submit/get-test?' + params.toString())
                .then(function(response) {
                    displayResult('get-result', response.data, 'success');
                })
                .catch(function(error) {
                    displayResult('get-result', error.response?.data || {error: error.message}, 'error');
                });
        }

        // 长时间处理测试
        function testLongProcess() {
            const data = {
                message: "长时间处理测试",
                timestamp: Date.now()
            };

            displayResult('long-process-result', {message: "请求发送中，请等待..."}, 'info');
            makeRequest('/test/duplicate-submit/long-process', data, 'long-process-result');
        }

        // 批量测试
        function batchTest() {
            const resultDiv = document.getElementById('batch-result');
            resultDiv.innerHTML = '开始批量测试...\n';

            const data = {
                message: "批量测试",
                batchId: Date.now()
            };

            for (let i = 1; i <= 5; i++) {
                setTimeout(function() {
                    axios.post('/test/duplicate-submit/basic', {...data, requestIndex: i})
                        .then(function(response) {
                            resultDiv.innerHTML += '请求' + i + ': 成功 - ' + response.data.message + '\n';
                        })
                        .catch(function(error) {
                            const errorData = error.response?.data || {error: error.message};
                            resultDiv.innerHTML += '请求' + i + ': 失败 - ' + errorData.message +
                                (errorData.remainingTime ? ' (剩余' + errorData.remainingTime + '秒)' : '') + '\n';
                        });
                }, i * 100); // 每100ms发送一个请求
            }
        }

        // 并发测试
        function testConcurrent() {
            const data = {
                message: "并发测试",
                timestamp: Date.now(),
                testType: "concurrent"
            };

            makeRequest('/test/duplicate-submit/concurrent-test', data, 'concurrent-result');
        }

        // SETNX原子性测试
        function testSetnx() {
            const data = {
                message: "SETNX原子性测试",
                timestamp: Date.now(),
                testType: "setnx",
                implementation: "Redis SETNX + Lua Script"
            };

            makeRequest('/test/duplicate-submit/setnx-test', data, 'setnx-result');
        }

        // 快速处理锁释放测试
        function testLockReleaseFast() {
            const data = {
                message: "快速处理锁释放测试",
                timestamp: Date.now(),
                testType: "lock-release-fast"
            };

            displayResult('lock-release-result', {message: "开始快速处理测试，预计1秒完成..."}, 'info');
            makeRequest('/test/duplicate-submit/lock-release-fast', data, 'lock-release-result');
        }

        // 长时间处理锁释放测试
        function testLockReleaseSlow() {
            const data = {
                message: "长时间处理锁释放测试",
                timestamp: Date.now(),
                testType: "lock-release-slow"
            };

            displayResult('lock-release-result', {message: "开始长时间处理测试，预计3秒完成..."}, 'info');
            makeRequest('/test/duplicate-submit/lock-release-slow', data, 'lock-release-result');
        }

        // Key生成优化测试
        function testKeyGenerationOptimization() {
            const params = new URLSearchParams({
                testIdentifier: 'TEST' + Date.now(),
                timestamp: Date.now().toString()
            });

            const data = {
                message: "Key生成优化测试",
                data: {
                    value: "optimized_key_generation",
                    type: "performance_test"
                },
                testType: "KEY_GENERATION_OPTIMIZATION"
            };

            axios.post('/test/duplicate-submit/key-generation-optimization?' + params.toString(), data)
                .then(function(response) {
                    displayResult('key-optimization-result', response.data, 'success');
                })
                .catch(function(error) {
                    displayResult('key-optimization-result', error.response?.data || {error: error.message}, 'error');
                });
        }

        // 通用请求方法
        function makeRequest(url, data, resultId) {
            axios.post(url, data)
                .then(function(response) {
                    displayResult(resultId, response.data, 'success');
                })
                .catch(function(error) {
                    displayResult(resultId, error.response?.data || {error: error.message}, 'error');
                });
        }

        // 显示结果
        function displayResult(elementId, data, type) {
            const element = document.getElementById(elementId);
            const timestamp = new Date().toLocaleTimeString();
            const result = '[' + timestamp + '] ' + JSON.stringify(data, null, 2);

            element.innerHTML = result;
            element.className = 'result-area ' + type;
        }

        // 页面加载时获取测试信息
        document.addEventListener('DOMContentLoaded', function() {
            axios.get('/test/duplicate-submit/info')
                .then(function(response) {
                    console.log('防重复提交测试信息:', response.data);
                })
                .catch(function(error) {
                    console.error('获取测试信息失败:', error);
                });
        });
    </script>
</body>
</html>
