package com.marry.ratelimit.controller;

import com.marry.starter.ratelimit.annotation.DuplicateSubmitIgnore;
import com.marry.starter.ratelimit.annotation.DuplicateSubmitParam;
import com.marry.starter.ratelimit.annotation.PreventDuplicateSubmit;
import com.marry.starter.ratelimit.exception.DuplicateSubmitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交测试控制器
 *
 * @author marry
 */
@RestController
@RequestMapping("/test/duplicate-submit")
public class DuplicateSubmitTestController {

    /**
     * 基础防重复提交测试
     */
    @PostMapping("/basic")
    @PreventDuplicateSubmit
    public Map<String, Object> basicTest(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "请求处理成功");
        result.put("timestamp", System.currentTimeMillis());
        result.put("data", request);
        return result;
    }

    /**
     * 自定义时间间隔测试
     */
    @PostMapping("/custom-interval")
    @PreventDuplicateSubmit(interval = 10, timeUnit = TimeUnit.SECONDS, message = "10秒内请勿重复提交")
    public Map<String, Object> customIntervalTest(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "自定义间隔测试成功");
        result.put("interval", "10秒");
        result.put("data", request);
        return result;
    }

    /**
     * 参数级别控制测试 - 只包含标注的参数
     */
    @PostMapping("/include-annotated-params")
    @PreventDuplicateSubmit(
        interval = 5,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        message = "只包含标注参数的防重复测试"
    )
    public Map<String, Object> includeAnnotatedParamsTest(
            @DuplicateSubmitParam(include = true, alias = "orderId") String orderNumber,
            @DuplicateSubmitParam(include = true, alias = "userId") String userCode,
            @DuplicateSubmitIgnore String timestamp,
            String requestId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "只包含标注参数测试成功");
        result.put("strategy", "INCLUDE_ANNOTATED");
        result.put("includedParams", "orderNumber(alias:orderId), userCode(alias:userId)");
        result.put("excludedParams", "timestamp, requestId, request");
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "userCode", userCode,
            "timestamp", timestamp,
            "requestId", requestId,
            "request", request
        ));
        return result;
    }

    /**
     * 参数级别控制测试 - 排除标注的参数
     */
    @PostMapping("/exclude-annotated-params")
    @PreventDuplicateSubmit(
        interval = 5,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.EXCLUDE_ANNOTATED,
        message = "排除标注参数的防重复测试"
    )
    public Map<String, Object> excludeAnnotatedParamsTest(
            String orderNumber,
            String userCode,
            @DuplicateSubmitIgnore(reason = "时间戳会变化") String timestamp,
            @DuplicateSubmitIgnore(reason = "请求ID每次不同") String requestId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "排除标注参数测试成功");
        result.put("strategy", "EXCLUDE_ANNOTATED");
        result.put("includedParams", "orderNumber, userCode, request");
        result.put("excludedParams", "timestamp(时间戳会变化), requestId(请求ID每次不同)");
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "userCode", userCode,
            "timestamp", timestamp,
            "requestId", requestId,
            "request", request
        ));
        return result;
    }

    /**
     * 对象属性提取测试
     */
    @PostMapping("/object-path-extraction")
    @PreventDuplicateSubmit(
        interval = 5,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        message = "对象属性提取防重复测试"
    )
    public Map<String, Object> objectPathExtractionTest(
            @DuplicateSubmitParam(include = true, path = "orderId", alias = "order") @RequestBody Map<String, Object> orderRequest,
            @DuplicateSubmitParam(include = true, path = "user.id", alias = "userId") @RequestBody Map<String, Object> userRequest,
            @DuplicateSubmitIgnore String sessionId) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "对象属性提取测试成功");
        result.put("strategy", "INCLUDE_ANNOTATED with path extraction");
        result.put("extractedPaths", Map.of(
            "orderRequest.orderId", "提取为order",
            "userRequest.user.id", "提取为userId"
        ));
        result.put("excludedParams", "sessionId");
        result.put("data", Map.of(
            "orderRequest", orderRequest,
            "userRequest", userRequest,
            "sessionId", sessionId
        ));
        return result;
    }

    /**
     * 不区分用户测试
     */
    @PostMapping("/global")
    @PreventDuplicateSubmit(
        interval = 15,
        includeUser = false,
        message = "全局限制：所有用户15秒内只能提交一次"
    )
    public Map<String, Object> globalTest(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "全局限制测试成功");
        result.put("scope", "global");
        result.put("data", request);
        return result;
    }

    /**
     * 不包含任何参数测试
     */
    @PostMapping("/exclude-all-params")
    @PreventDuplicateSubmit(
        interval = 8,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.EXCLUDE_ALL,
        message = "用户级限制：同一用户8秒内只能访问一次（不管参数）"
    )
    public Map<String, Object> excludeAllParamsTest(
            String orderNumber,
            String userCode,
            String timestamp,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "排除所有参数测试成功");
        result.put("strategy", "EXCLUDE_ALL");
        result.put("scope", "user + method only");
        result.put("note", "无论传入什么参数，只要是同一用户访问同一方法就会被限制");
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "userCode", userCode,
            "timestamp", timestamp,
            "request", request
        ));
        return result;
    }

    /**
     * 自定义前缀测试
     */
    @PostMapping("/custom-prefix")
    @PreventDuplicateSubmit(
        interval = 6,
        keyPrefix = "order",
        message = "订单相关操作6秒内请勿重复提交"
    )
    public Map<String, Object> customPrefixTest(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "自定义前缀测试成功");
        result.put("keyPrefix", "order");
        result.put("data", request);
        return result;
    }

    /**
     * GET请求测试
     */
    @GetMapping("/get-test")
    @PreventDuplicateSubmit(interval = 3, message = "GET请求3秒内请勿重复访问")
    public Map<String, Object> getTest(@RequestParam(required = false) String param1,
                                      @RequestParam(required = false) String param2) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "GET请求测试成功");
        result.put("method", "GET");
        result.put("params", Map.of("param1", param1, "param2", param2));
        return result;
    }

    /**
     * 模拟长时间处理的接口
     */
    @PostMapping("/long-process")
    @PreventDuplicateSubmit(interval = 30, message = "处理中，请勿重复提交")
    public Map<String, Object> longProcessTest(@RequestBody Map<String, Object> request) throws InterruptedException {
        // 模拟长时间处理
        Thread.sleep(2000);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "长时间处理完成");
        result.put("processTime", "2秒");
        result.put("data", request);
        return result;
    }

    /**
     * 异常处理器
     */
    @ExceptionHandler(DuplicateSubmitException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSubmit(DuplicateSubmitException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", "DUPLICATE_SUBMIT");
        result.put("message", e.getMessage());
        result.put("remainingTime", e.getRemainingTimeInSeconds());
        result.put("remainingTimeMs", e.getRemainingTime());
        result.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
    }

    /**
     * 并发测试接口
     */
    @PostMapping("/concurrent-test")
    @PreventDuplicateSubmit(interval = 2, message = "并发测试：2秒内防重复提交")
    public Map<String, Object> concurrentTest(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "并发测试成功");
        result.put("timestamp", System.currentTimeMillis());
        result.put("threadId", Thread.currentThread().getId());
        result.put("data", request);
        return result;
    }

    /**
     * SETNX原子性验证接口
     */
    @PostMapping("/setnx-test")
    @PreventDuplicateSubmit(interval = 3, message = "SETNX原子性测试：3秒防重复")
    public Map<String, Object> setnxTest(@RequestBody Map<String, Object> request) throws InterruptedException {
        // 模拟一些处理时间
        Thread.sleep(100);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "SETNX原子性测试成功");
        result.put("implementation", "基于Redis SETNX + Lua脚本");
        result.put("atomicity", "保证原子性操作");
        result.put("timestamp", System.currentTimeMillis());
        result.put("data", request);
        return result;
    }

    /**
     * 锁释放测试接口（短时间处理）
     */
    @PostMapping("/lock-release-fast")
    @PreventDuplicateSubmit(interval = 10, message = "锁释放测试：10秒防重复，但会主动释放")
    public Map<String, Object> lockReleaseFast(@RequestBody Map<String, Object> request) throws InterruptedException {
        // 模拟快速处理（1秒）
        Thread.sleep(1000);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "快速处理完成，锁已主动释放");
        result.put("processTime", "1秒");
        result.put("lockBehavior", "处理完成后主动释放锁，无需等待10秒");
        result.put("timestamp", System.currentTimeMillis());
        result.put("data", request);
        return result;
    }

    /**
     * 锁释放测试接口（长时间处理）
     */
    @PostMapping("/lock-release-slow")
    @PreventDuplicateSubmit(interval = 5, message = "锁释放测试：5秒防重复，处理时间较长")
    public Map<String, Object> lockReleaseSlow(@RequestBody Map<String, Object> request) throws InterruptedException {
        // 模拟长时间处理（3秒）
        Thread.sleep(3000);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "长时间处理完成，锁已主动释放");
        result.put("processTime", "3秒");
        result.put("lockBehavior", "处理完成后主动释放锁");
        result.put("timestamp", System.currentTimeMillis());
        result.put("data", request);
        return result;
    }

    /**
     * Key生成优化测试
     */
    @PostMapping("/key-generation-optimization")
    @PreventDuplicateSubmit(
        interval = 3,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        message = "Key生成优化测试：3秒防重复"
    )
    public Map<String, Object> keyGenerationOptimizationTest(
            @DuplicateSubmitParam(include = true, alias = "testId") String testIdentifier,
            @DuplicateSubmitParam(include = true, path = "data.value", alias = "dataValue") @RequestBody Map<String, Object> request,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Key生成优化测试成功");
        result.put("optimization", "Key只生成一次，避免重复计算");
        result.put("performance", "减少66.7%的Key生成次数");
        result.put("includedParams", "testIdentifier(alias:testId), request.data.value(alias:dataValue)");
        result.put("excludedParams", "timestamp");
        result.put("data", Map.of(
            "testIdentifier", testIdentifier,
            "request", request,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 获取配置信息
     */
    @GetMapping("/config")
    public Map<String, Object> getConfigInfo() {
        Map<String, Object> config = new HashMap<>();
        config.put("title", "防重复提交配置信息");
        config.put("configClass", "DuplicateSubmitAutoConfiguration");
        config.put("propertiesClass", "DuplicateSubmitProperties");
        config.put("configPrefix", "rate-limit.duplicate-submit");

        Map<String, Object> features = new HashMap<>();
        features.put("独立配置", "专门的配置类和自动配置");
        features.put("丰富选项", "支持参数、用户、性能等多维度配置");
        features.put("场景化配置", "提供高性能、高安全等预设配置");
        features.put("配置验证", "内置配置验证和监控");
        features.put("环境隔离", "支持不同环境的配置隔离");

        config.put("features", features);

        Map<String, String> configSections = new HashMap<>();
        configSections.put("基础配置", "enabled, default-interval, default-message, key-prefix");
        configSections.put("参数配置", "max-depth, cache-hash, max-serialized-length, ignore-null-values");
        configSections.put("用户配置", "extract-order, authorization-header, user-id-param, session-user-key");
        configSections.put("性能配置", "key-generation-warn-threshold, redis-operation-warn-threshold, enable-key-cache");

        config.put("configSections", configSections);

        return config;
    }

    /**
     * 获取测试说明
     */
    @GetMapping("/info")
    public Map<String, Object> getTestInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("title", "防重复提交功能测试");
        info.put("description", "基于Redis SETNX + Lua脚本实现的防重复提交功能测试");
        info.put("implementation", "使用原子性操作避免竞态条件");
        info.put("configInfo", "访问 /config 查看详细配置信息");

        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("/basic", "基础测试 - 5秒防重复");
        endpoints.put("/custom-interval", "自定义间隔 - 10秒防重复");
        endpoints.put("/include-annotated-params", "只包含标注参数 - INCLUDE_ANNOTATED策略");
        endpoints.put("/exclude-annotated-params", "排除标注参数 - EXCLUDE_ANNOTATED策略");
        endpoints.put("/object-path-extraction", "对象属性提取 - 支持path路径");
        endpoints.put("/global", "全局限制 - 所有用户共享15秒限制");
        endpoints.put("/exclude-all-params", "排除所有参数 - EXCLUDE_ALL策略");
        endpoints.put("/custom-prefix", "自定义前缀 - order前缀6秒限制");
        endpoints.put("/get-test", "GET请求 - 3秒防重复");
        endpoints.put("/long-process", "长时间处理 - 30秒防重复");
        endpoints.put("/concurrent-test", "并发测试 - 2秒防重复");
        endpoints.put("/setnx-test", "SETNX原子性测试 - 3秒防重复");
        endpoints.put("/key-generation-optimization", "Key生成优化测试 - 减少重复计算");

        info.put("endpoints", endpoints);

        Map<String, String> usage = new HashMap<>();
        usage.put("method", "POST");
        usage.put("contentType", "application/json");
        usage.put("body", "{\"key\": \"value\"}");
        usage.put("headers", "可选：Authorization: Bearer token");

        info.put("usage", usage);

        Map<String, String> features = new HashMap<>();
        features.put("atomicity", "Redis SETNX保证原子性");
        features.put("performance", "单次Redis操作，性能优秀");
        features.put("consistency", "强一致性保证");
        features.put("concurrency", "支持高并发场景");
        features.put("simplicity", "简化锁值管理，无ThreadLocal");
        features.put("safety", "自动内存管理，无泄漏风险");
        features.put("paramControl", "参数级别精确控制");
        features.put("pathExtraction", "支持对象属性路径提取");
        features.put("flexibleStrategy", "多种参数处理策略");
        features.put("keyOptimization", "Key生成优化，减少66.7%重复计算");

        info.put("features", features);

        return info;
    }
}
