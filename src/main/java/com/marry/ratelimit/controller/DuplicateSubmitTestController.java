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
     * 参数分组测试
     */
    @PostMapping("/param-group-test")
    @PreventDuplicateSubmit(
        interval = 8,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.ALL_GROUPS,
        orderByWeight = true,
        message = "参数分组测试：8秒防重复"
    )
    public Map<String, Object> paramGroupTest(
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderId") String orderNumber,
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderType") String orderType,
            @DuplicateSubmitParam(include = true, group = "user", groupWeight = 5, alias = "userId") String userCode,
            @DuplicateSubmitParam(include = true, group = "payment", groupWeight = 8, alias = "paymentMethod") String paymentMethod,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "参数分组测试成功");
        result.put("feature", "每个分组独立进行防重复校验");
        result.put("strategy", "INCLUDE_ANNOTATED + ALL_GROUPS，按权重排序");

        Map<String, Object> groups = new HashMap<>();
        groups.put("order", Map.of(
            "weight", 10,
            "params", "orderNumber(orderId), orderType",
            "description", "订单相关参数，权重最高"
        ));
        groups.put("payment", Map.of(
            "weight", 8,
            "params", "paymentMethod",
            "description", "支付相关参数，权重中等"
        ));
        groups.put("user", Map.of(
            "weight", 5,
            "params", "userCode(userId)",
            "description", "用户相关参数，权重较低"
        ));

        result.put("groups", groups);
        result.put("checkOrder", "order(权重10) -> payment(权重8) -> user(权重5)");
        result.put("excludedParams", "timestamp(被@DuplicateSubmitIgnore标注)");
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "orderType", orderType,
            "userCode", userCode,
            "userInfo", userInfo,
            "paymentMethod", paymentMethod,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 单个分组测试
     */
    @PostMapping("/single-group-test")
    @PreventDuplicateSubmit(
        interval = 5,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.ALL_GROUPS,
        message = "单个分组测试：5秒防重复"
    )
    public Map<String, Object> singleGroupTest(
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderId") String orderNumber,
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderType") String orderType,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "单个分组测试成功");
        result.put("feature", "验证只有一个分组时的锁值格式和释放逻辑");
        result.put("strategy", "INCLUDE_ANNOTATED + ALL_GROUPS");
        result.put("lockFormat", "groupName:groupKey:lockValue (无|分隔符)");
        result.put("groups", Map.of(
            "order", Map.of(
                "weight", 10,
                "params", "orderNumber(orderId), orderType",
                "description", "只有一个order分组"
            )
        ));
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "orderType", orderType,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 指定分组测试
     */
    @PostMapping("/specified-group-test")
    @PreventDuplicateSubmit(
        interval = 6,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.SPECIFIED_GROUPS,
        groups = {"order", "payment"},
        orderByWeight = true,
        message = "指定分组测试：6秒防重复"
    )
    public Map<String, Object> specifiedGroupTest(
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderId") String orderNumber,
            @DuplicateSubmitParam(include = true, group = "user", groupWeight = 5, alias = "userId") String userCode,
            @DuplicateSubmitParam(include = true, group = "payment", groupWeight = 8, alias = "paymentMethod") String paymentMethod,
            @DuplicateSubmitParam(include = true, group = "shipping", groupWeight = 3, alias = "address") String shippingAddress) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "指定分组测试成功");
        result.put("feature", "只检查指定的分组");
        result.put("strategy", "SPECIFIED_GROUPS: [order, payment]");
        result.put("checkedGroups", "order(权重10), payment(权重8)");
        result.put("ignoredGroups", "user(权重5), shipping(权重3)");
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "userCode", userCode,
            "paymentMethod", paymentMethod,
            "shippingAddress", shippingAddress,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 稳定参数名称测试
     */
    @PostMapping("/stable-param-test")
    @PreventDuplicateSubmit(
        interval = 6,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.ALL_GROUPS,
        message = "稳定参数名称测试：6秒防重复"
    )
    public Map<String, Object> stableParamTest(
            @DuplicateSubmitParam(include = true, group = "products", processor = "split", alias = "productIds") String productList,
            @DuplicateSubmitParam(include = true, group = "orders", processor = "split", alias = "orderIds") String orderList,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "稳定参数名称测试成功");
        result.put("feature", "基于内容生成稳定的分组名称和参数名称，完全解决集合顺序问题");

        Map<String, Object> stableNaming = new HashMap<>();
        stableNaming.put("problem", "集合元素顺序变化导致分组名和参数名都不同，影响MD5计算");
        stableNaming.put("solution", "分组名和参数名都基于元素内容生成");
        stableNaming.put("groupExample", Map.of(
            "input1", "PROD001,PROD002,PROD003",
            "groups1", "products_PROD001_hash1, products_PROD002_hash2, products_PROD003_hash3",
            "input2", "PROD003,PROD001,PROD002",
            "groups2", "products_PROD001_hash1, products_PROD002_hash2, products_PROD003_hash3",
            "result", "相同内容生成相同分组名"
        ));
        stableNaming.put("paramExample", Map.of(
            "input1", "PROD001,PROD002,PROD003",
            "params1", "productIds[PROD001], productIds[PROD002], productIds[PROD003]",
            "input2", "PROD003,PROD001,PROD002",
            "params2", "productIds[PROD001], productIds[PROD002], productIds[PROD003]",
            "result", "相同内容生成相同参数名"
        ));

        result.put("stableNaming", stableNaming);
        result.put("benefits", Map.of(
            "groupStability", "分组名称基于内容，顺序无关",
            "paramStability", "参数名称基于内容，顺序无关",
            "md5Consistency", "MD5计算结果一致，防重复校验正确",
            "orderIndependent", "完全不受集合元素顺序影响"
        ));
        result.put("implementation", Map.of(
            "groupName", "baseGroupName_safeContent_contentHash",
            "paramName", "baseParamName[safeContent]",
            "md5Input", "稳定的分组名称和参数名称组合"
        ));
        result.put("data", Map.of(
            "productList", productList,
            "orderList", orderList,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 稳定分组名称测试
     */
    @PostMapping("/stable-group-test")
    @PreventDuplicateSubmit(
        interval = 7,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.ALL_GROUPS,
        message = "稳定分组名称测试：7秒防重复"
    )
    public Map<String, Object> stableGroupTest(
            @DuplicateSubmitParam(include = true, group = "products", processor = "split", alias = "productIds") String productList,
            @DuplicateSubmitParam(include = true, group = "orders", processor = "split", alias = "orderIds") String orderList,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "稳定分组名称测试成功");
        result.put("feature", "基于内容生成稳定的分组名称，解决集合顺序变化问题");

        Map<String, Object> groupNaming = new HashMap<>();
        groupNaming.put("problem", "集合元素顺序变化导致分组名不同，校验失败");
        groupNaming.put("solution", "基于元素内容生成稳定的分组名称");
        groupNaming.put("example", Map.of(
            "input1", "PROD001,PROD002,PROD003",
            "groups1", "products_PROD001_hash1, products_PROD002_hash2, products_PROD003_hash3",
            "input2", "PROD003,PROD001,PROD002",
            "groups2", "products_PROD001_hash1, products_PROD002_hash2, products_PROD003_hash3",
            "result", "相同内容生成相同分组名，顺序无关"
        ));

        result.put("groupNaming", groupNaming);
        result.put("benefits", Map.of(
            "stability", "相同内容总是生成相同的分组名称",
            "orderIndependent", "集合元素顺序变化不影响校验",
            "contentBased", "基于元素内容而非索引位置",
            "collision", "使用哈希避免内容相似导致的冲突"
        ));
        result.put("data", Map.of(
            "productList", productList,
            "orderList", orderList,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 集合处理器测试
     */
    @PostMapping("/collection-processor-test")
    @PreventDuplicateSubmit(
        interval = 8,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.ALL_GROUPS,
        message = "集合处理器测试：8秒防重复"
    )
    public Map<String, Object> collectionProcessorTest(
            @DuplicateSubmitParam(include = true, group = "products", processor = "split", alias = "productIds") String productList,
            @DuplicateSubmitParam(include = true, group = "roles", processor = "custom_collection", alias = "userRoles") String roleList,
            @DuplicateSubmitParam(include = true, group = "tags", processor = "split", alias = "tagList") String tags,
            @DuplicateSubmitParam(include = true, group = "categories", processor = "custom_collection", path = "data.categories", alias = "categoryList") @RequestBody Map<String, Object> requestData,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "集合处理器测试成功");
        result.put("feature", "集合中的每个元素作为独立的组别进行防重复校验");

        Map<String, Object> processors = new HashMap<>();
        processors.put("split", Map.of(
            "description", "分割处理器，按分隔符分割成多个值",
            "example", "PROD001,PROD002,PROD003 → [PROD001, PROD002, PROD003]",
            "grouping", "每个产品ID作为独立的products_0, products_1, products_2分组"
        ));
        processors.put("custom_collection", Map.of(
            "description", "自定义集合处理器，处理复杂对象并返回多个值",
            "example", "admin,user,guest → [ROLE_ADMIN, ROLE_USER, ROLE_GUEST]",
            "grouping", "每个角色作为独立的roles_0, roles_1, roles_2分组"
        ));

        result.put("processors", processors);
        result.put("groupStrategy", "ALL_GROUPS - 检查所有生成的分组");
        result.put("collectionBehavior", "集合中每个元素生成独立分组，实现单个控制效果");
        result.put("data", Map.of(
            "productList", productList,
            "roleList", roleList,
            "tags", tags,
            "requestData", requestData,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 参数值处理器测试
     */
    @PostMapping("/processor-test")
    @PreventDuplicateSubmit(
        interval = 6,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        message = "参数值处理器测试：6秒防重复"
    )
    public Map<String, Object> processorTest(
            @DuplicateSubmitParam(include = true, processor = "default", alias = "defaultValue") String defaultParam,
            @DuplicateSubmitParam(include = true, processor = "hash", alias = "hashedValue") String hashParam,
            @DuplicateSubmitParam(include = true, processor = "mask", alias = "maskedValue") String sensitiveParam,
            @DuplicateSubmitParam(include = true, processor = "normalize", alias = "normalizedValue") String normalizeParam,
            @DuplicateSubmitParam(include = true, processor = "custom_order", alias = "customOrderValue") String orderNumber,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "参数值处理器测试成功");
        result.put("feature", "使用不同的处理器处理参数值");

        Map<String, Object> processors = new HashMap<>();
        processors.put("default", Map.of(
            "description", "默认处理器，直接返回原始值",
            "input", defaultParam,
            "alias", "defaultValue"
        ));
        processors.put("hash", Map.of(
            "description", "哈希处理器，将值转换为MD5哈希",
            "input", hashParam,
            "alias", "hashedValue"
        ));
        processors.put("mask", Map.of(
            "description", "掩码处理器，对敏感信息进行掩码",
            "input", sensitiveParam,
            "alias", "maskedValue"
        ));
        processors.put("normalize", Map.of(
            "description", "标准化处理器，去空格、转小写等",
            "input", normalizeParam,
            "alias", "normalizedValue"
        ));
        processors.put("custom_order", Map.of(
            "description", "自定义订单处理器，订单号标准化",
            "input", orderNumber,
            "alias", "customOrderValue"
        ));

        result.put("processors", processors);
        result.put("excludedParams", "timestamp(被@DuplicateSubmitIgnore标注)");
        result.put("data", Map.of(
            "defaultParam", defaultParam,
            "hashParam", hashParam,
            "sensitiveParam", sensitiveParam,
            "normalizeParam", normalizeParam,
            "orderNumber", orderNumber,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * Redis SETNX测试
     */
    @PostMapping("/redis-setnx-test")
    @PreventDuplicateSubmit(
        interval = 5,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ALL,
        message = "Redis SETNX测试：5秒防重复"
    )
    public Map<String, Object> redisSetnxTest(String testParam, String userId) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Redis SETNX测试成功");
        result.put("feature", "使用Redis SET NX PX命令实现原子性防重复提交");

        Map<String, Object> setnxDetails = new HashMap<>();
        setnxDetails.put("command", "SET key value NX PX milliseconds");
        setnxDetails.put("description", "原子性地设置key和过期时间，仅当key不存在时");
        setnxDetails.put("advantages", Map.of(
            "atomicity", "设置key和过期时间在一个原子操作中完成",
            "concurrency", "完美处理并发情况，避免竞态条件",
            "efficiency", "比SETNX+PEXPIRE两步操作更高效",
            "reliability", "避免SETNX成功但PEXPIRE失败的情况"
        ));

        Map<String, Object> luaScript = new HashMap<>();
        luaScript.put("purpose", "原子性检查和设置防重复提交key");
        luaScript.put("command", "SET key value NX PX ttl");
        luaScript.put("returnValue", Map.of(
            "0", "设置成功，不是重复提交",
            ">0", "key已存在，返回剩余TTL（毫秒）"
        ));
        luaScript.put("benefits", Map.of(
            "atomicity", "整个检查和设置过程是原子性的",
            "consistency", "避免并发情况下的数据不一致",
            "performance", "减少网络往返次数"
        ));

        result.put("setnxDetails", setnxDetails);
        result.put("luaScript", luaScript);
        result.put("implementation", Map.of(
            "step1", "执行Lua脚本：SET key value NX PX ttl",
            "step2", "如果返回OK，表示设置成功，允许请求",
            "step3", "如果返回nil，表示key已存在，获取剩余TTL",
            "step4", "返回剩余时间，拒绝重复请求"
        ));
        result.put("data", Map.of(
            "testParam", testParam,
            "userId", userId,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 统一处理架构测试
     */
    @PostMapping("/unified-processing-test")
    @PreventDuplicateSubmit(
        interval = 4,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ALL,
        message = "统一处理架构测试：4秒防重复"
    )
    public Map<String, Object> unifiedProcessingTest(String param1, String param2) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "统一处理架构测试成功");
        result.put("feature", "传统方式也通过分组处理流程，统一架构");
        result.put("architecture", "tryAcquireLock → generateParamGroups → tryAcquireGroupLocks");
        result.put("lockFormat", "default:traditional_key:lock_value");
        result.put("processing", Map.of(
            "step1", "生成参数分组",
            "step2", "检测到传统情况，创建默认分组",
            "step3", "统一使用tryAcquireGroupLocks处理",
            "step4", "识别默认分组，使用传统key"
        ));
        result.put("benefits", Map.of(
            "codeSimplification", "统一处理逻辑，减少代码重复",
            "errorHandling", "传统方式也享受分组的回滚机制",
            "compatibility", "完全向后兼容，API接口不变",
            "maintainability", "更好的扩展性和维护性"
        ));
        result.put("data", Map.of(
            "param1", param1,
            "param2", param2,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 参数注解获取修复测试
     */
    @PostMapping("/annotation-fix-test")
    @PreventDuplicateSubmit(
        interval = 5,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        message = "参数注解获取修复测试：5秒防重复"
    )
    public Map<String, Object> annotationFixTest(
            @DuplicateSubmitParam(include = true, alias = "orderId") String orderNumber,
            @DuplicateSubmitParam(include = true, path = "user.id", alias = "userId") @RequestBody Map<String, Object> userRequest,
            @DuplicateSubmitIgnore(reason = "会话ID不参与防重复") String sessionId,
            String normalParam) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "参数注解获取修复测试成功");
        result.put("fix", "通过方法签名获取注解，解决Spring环境下注解获取失败问题");
        result.put("strategy", "INCLUDE_ANNOTATED - 只包含标注的参数");
        result.put("includedParams", Map.of(
            "orderNumber", "alias: orderId",
            "userRequest.user.id", "alias: userId, path: user.id"
        ));
        result.put("excludedParams", Map.of(
            "sessionId", "被@DuplicateSubmitIgnore标注",
            "normalParam", "未被@DuplicateSubmitParam标注"
        ));
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "userRequest", userRequest,
            "sessionId", sessionId,
            "normalParam", normalParam,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 参数分组测试
     */
    @PostMapping("/param-group-test")
    @PreventDuplicateSubmit(
        interval = 8,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.ALL_GROUPS,
        orderByWeight = true,
        message = "参数分组测试：8秒防重复"
    )
    public Map<String, Object> paramGroupTest(
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderId") String orderNumber,
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderType") String orderType,
            @DuplicateSubmitParam(include = true, group = "user", groupWeight = 5, alias = "userId") String userCode,
            @DuplicateSubmitParam(include = true, group = "user", groupWeight = 5, path = "profile.level", alias = "userLevel") @RequestBody Map<String, Object> userInfo,
            @DuplicateSubmitParam(include = true, group = "payment", groupWeight = 8, alias = "paymentMethod") String paymentMethod,
            @DuplicateSubmitIgnore String timestamp) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "参数分组测试成功");
        result.put("feature", "每个分组独立进行防重复校验");
        result.put("strategy", "INCLUDE_ANNOTATED + ALL_GROUPS，按权重排序");

        Map<String, Object> groups = new HashMap<>();
        groups.put("order", Map.of(
            "weight", 10,
            "params", "orderNumber(orderId), orderType",
            "description", "订单相关参数，权重最高"
        ));
        groups.put("payment", Map.of(
            "weight", 8,
            "params", "paymentMethod",
            "description", "支付相关参数，权重中等"
        ));
        groups.put("user", Map.of(
            "weight", 5,
            "params", "userCode(userId), userInfo.profile.level(userLevel)",
            "description", "用户相关参数，权重较低"
        ));

        result.put("groups", groups);
        result.put("checkOrder", "order(权重10) -> payment(权重8) -> user(权重5)");
        result.put("excludedParams", "timestamp(被@DuplicateSubmitIgnore标注)");
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "orderType", orderType,
            "userCode", userCode,
            "userInfo", userInfo,
            "paymentMethod", paymentMethod,
            "timestamp", timestamp,
            "processTime", System.currentTimeMillis()
        ));
        return result;
    }

    /**
     * 指定分组测试
     */
    @PostMapping("/specified-group-test")
    @PreventDuplicateSubmit(
        interval = 6,
        paramStrategy = PreventDuplicateSubmit.ParamStrategy.INCLUDE_ANNOTATED,
        groupStrategy = PreventDuplicateSubmit.GroupStrategy.SPECIFIED_GROUPS,
        groups = {"order", "payment"},
        orderByWeight = true,
        message = "指定分组测试：6秒防重复"
    )
    public Map<String, Object> specifiedGroupTest(
            @DuplicateSubmitParam(include = true, group = "order", groupWeight = 10, alias = "orderId") String orderNumber,
            @DuplicateSubmitParam(include = true, group = "user", groupWeight = 5, alias = "userId") String userCode,
            @DuplicateSubmitParam(include = true, group = "payment", groupWeight = 8, alias = "paymentMethod") String paymentMethod,
            @DuplicateSubmitParam(include = true, group = "shipping", groupWeight = 3, alias = "address") String shippingAddress) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "指定分组测试成功");
        result.put("feature", "只检查指定的分组");
        result.put("strategy", "SPECIFIED_GROUPS: [order, payment]");
        result.put("checkedGroups", "order(权重10), payment(权重8)");
        result.put("ignoredGroups", "user(权重5), shipping(权重3)");
        result.put("data", Map.of(
            "orderNumber", orderNumber,
            "userCode", userCode,
            "paymentMethod", paymentMethod,
            "shippingAddress", shippingAddress,
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
        endpoints.put("/param-group-test", "参数分组测试 - 分组独立防重复校验");
        endpoints.put("/single-group-test", "单个分组测试 - 验证单分组锁值格式");
        endpoints.put("/specified-group-test", "指定分组测试 - 只检查指定分组");
        endpoints.put("/stable-param-test", "稳定参数名称测试 - 完全解决集合顺序问题");
        endpoints.put("/stable-group-test", "稳定分组名称测试 - 解决集合顺序变化问题");
        endpoints.put("/collection-processor-test", "集合处理器测试 - 集合元素独立分组控制");
        endpoints.put("/processor-test", "参数值处理器测试 - 自定义参数值处理");
        endpoints.put("/redis-setnx-test", "Redis SETNX测试 - 原子性防重复提交");
        endpoints.put("/unified-processing-test", "统一处理架构测试 - 传统方式分组化处理");
        endpoints.put("/annotation-fix-test", "参数注解获取修复测试 - 解决Spring环境兼容性");

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
        features.put("paramGrouping", "参数分组功能，每个分组独立防重复校验");
        features.put("groupStrategy", "支持多种分组策略：ALL_GROUPS, SPECIFIED_GROUPS, EXCEPT_GROUPS");
        features.put("groupWeight", "分组权重排序，高权重分组优先检查");
        features.put("lockRollback", "分组锁失败时自动回滚已获取的锁");
        features.put("valueProcessor", "参数值处理器，支持自定义参数值处理逻辑");
        features.put("builtinProcessors", "内置处理器：default, hash, mask, normalize, split");
        features.put("customProcessor", "支持自定义处理器，可在引用项目中扩展");
        features.put("collectionProcessor", "集合处理器，集合中每个元素作为独立分组控制");
        features.put("independentControl", "实现单个元素的独立防重复控制效果");
        features.put("stableGroupNames", "稳定分组名称，基于内容生成，解决集合顺序变化问题");
        features.put("stableParamNames", "稳定参数名称，基于内容生成，确保MD5计算一致");
        features.put("contentBasedNaming", "分组名称和参数名称都基于元素内容而非索引位置");
        features.put("orderIndependent", "集合元素顺序变化完全不影响防重复校验");
        features.put("md5Consistency", "MD5计算结果一致，防重复校验准确可靠");
        features.put("redisSetnx", "Redis SETNX实现，使用SET NX PX原子性操作");
        features.put("atomicOperation", "原子性操作，避免并发情况下的竞态条件");
        features.put("luaScript", "Lua脚本保证检查和设置的原子性");
        features.put("unifiedProcessing", "统一处理架构，传统方式和分组方式使用相同流程");
        features.put("codeSimplification", "代码简化，减少重复逻辑和判断分支");
        features.put("enhancedErrorHandling", "增强错误处理，传统方式也享受分组回滚机制");
        features.put("annotationFix", "参数注解获取修复，解决Spring环境兼容性问题");

        info.put("features", features);

        return info;
    }
}
