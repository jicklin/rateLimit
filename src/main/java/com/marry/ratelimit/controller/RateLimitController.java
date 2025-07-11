package com.marry.ratelimit.controller;

import com.marry.ratelimit.model.HttpMethod;
import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.model.RateLimitStats;
import com.marry.ratelimit.model.DetailedRateLimitStats;
import com.marry.ratelimit.model.RateLimitRecord;
import com.marry.ratelimit.service.RateLimitConfigService;
import com.marry.ratelimit.service.RateLimitService;
import com.marry.ratelimit.service.RateLimitStatsService;
import com.marry.ratelimit.service.impl.OptimizedWebRateLimitStatsService;
import com.marry.ratelimit.util.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 限流管理控制器
 */
@Controller
@RequestMapping("/ratelimit")
public class RateLimitController {


    private static final Logger logger = LoggerFactory.getLogger(RateLimitController.class);
    @Autowired
    private RateLimitConfigService configService;

    @Autowired
    private RateLimitStatsService statsService;

    @Autowired(required = false)
    private OptimizedWebRateLimitStatsService optimizedStatsService;

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * 主页面
     */
    @GetMapping("/")
    public String index(Model model) {

        List<RateLimitRule> rules = configService.getAllRules();
        List<RateLimitStats> stats = statsService.getAllStats();
        Map<String, Object> globalStats = statsService.getGlobalStats();

        model.addAttribute("rules", rules);
        model.addAttribute("stats", stats);
        model.addAttribute("globalStats", globalStats);
        model.addAttribute("httpMethods", HttpMethod.values());

        // 添加工具方法到模板中
        model.addAttribute("mathUtils", new MathUtils());

        return "ratelimit/index";
    }

    /**
     * 配置页面
     */
    @GetMapping("/config")
    public String config(Model model) {
        model.addAttribute("httpMethods", HttpMethod.values());
        return "ratelimit/config";
    }

    /**
     * 统计页面
     */
    @GetMapping("/stats")
    public String stats(Model model) {
        List<RateLimitStats> stats = statsService.getAllStats();
        Map<String, Object> globalStats = statsService.getGlobalStats();

        model.addAttribute("stats", stats);
        model.addAttribute("globalStats", globalStats);

        // 添加工具方法到模板中
        model.addAttribute("mathUtils", new MathUtils());

        return "ratelimit/stats";
    }

    /**
     * 优化统计页面
     */
    @GetMapping("/optimized-stats")
    public String optimizedStats(Model model) {
        try {
            List<RateLimitRule> rules = configService.getAllRules();
            model.addAttribute("rules", rules);

            // 检查是否启用优化模式
            model.addAttribute("optimizedMode", optimizedStatsService != null);

            return "optimized-stats";
        } catch (Exception e) {
            logger.error("加载优化统计页面异常", e);
            model.addAttribute("error", "加载页面失败");
            return "error";
        }
    }

    /**
     * 获取所有规则
     */
    @GetMapping("/api/rules")
    @ResponseBody
    public ResponseEntity<List<RateLimitRule>> getAllRules() {
        List<RateLimitRule> rules = configService.getAllRules();
        return ResponseEntity.ok(rules);
    }

    /**
     * 获取单个规则
     */
    @GetMapping("/api/rules/{id}")
    @ResponseBody
    public ResponseEntity<RateLimitRule> getRule(@PathVariable String id) {
        RateLimitRule rule = configService.getRule(id);
        if (rule != null) {
            return ResponseEntity.ok(rule);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 保存规则
     */
    @PostMapping("/api/rules")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveRule(@RequestBody RateLimitRule rule) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 验证规则
            if (rule.getName() == null || rule.getName().trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "规则名称不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (rule.getPathPattern() == null || rule.getPathPattern().trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "路径模式不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (rule.getBucketCapacity() <= 0 || rule.getRefillRate() <= 0 || rule.getTimeWindow() <= 0) {
                result.put("success", false);
                result.put("message", "令牌桶参数必须大于0");
                return ResponseEntity.badRequest().body(result);
            }

            RateLimitRule savedRule = configService.saveRule(rule);
            result.put("success", true);
            result.put("message", "保存成功");
            result.put("rule", savedRule);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/api/rules/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            configService.deleteRule(id);
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 切换规则状态
     */
    @PutMapping("/api/rules/{id}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable String id, @RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        try {
            configService.toggleRule(id, enabled);
            result.put("success", true);
            result.put("message", enabled ? "启用成功" : "禁用成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 更新规则优先级
     */
    @PutMapping("/api/rules/{id}/priority")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePriority(@PathVariable String id, @RequestParam int priority) {
        Map<String, Object> result = new HashMap<>();
        try {
            configService.updatePriority(id, priority);
            result.put("success", true);
            result.put("message", "更新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<List<RateLimitStats>> getStats() {
        List<RateLimitStats> stats = statsService.getAllStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取全局统计信息
     */
    @GetMapping("/api/stats/global")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> globalStats = statsService.getGlobalStats();
        return ResponseEntity.ok(globalStats);
    }

    /**
     * 重置统计信息
     */
    @DeleteMapping("/api/stats/{ruleId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetStats(@PathVariable String ruleId) {
        Map<String, Object> result = new HashMap<>();
        try {
            statsService.resetStats(ruleId);
            result.put("success", true);
            result.put("message", "重置成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 重置所有统计信息
     */
    @DeleteMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetAllStats() {
        Map<String, Object> result = new HashMap<>();
        try {
            statsService.resetAllStats();
            result.put("success", true);
            result.put("message", "重置成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 重置限流状态
     */
    @DeleteMapping("/api/reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetRateLimit() {
        Map<String, Object> result = new HashMap<>();
        try {
            rateLimitService.resetAll();
            result.put("success", true);
            result.put("message", "重置成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取详细统计信息（IP和用户维度）
     * 支持优化模式和标准模式
     */
    @GetMapping("/api/stats/{ruleId}/detailed")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDetailedStats(@PathVariable String ruleId) {
        try {
            Map<String, Object> result = new HashMap<>();

            if (optimizedStatsService != null) {
                // 使用优化统计服务
                Map<String, Object> optimizedStats = optimizedStatsService.getDetailedStatsMap(ruleId);
                result.put("mode", "optimized");
                result.put("data", optimizedStats);
            } else {
                // 使用标准统计服务
                List<DetailedRateLimitStats> detailedStats = statsService.getDetailedStats(ruleId);
                result.put("mode", "standard");
                result.put("data", detailedStats);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取详细统计异常: " + ruleId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取统计失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取IP维度统计信息
     */
    @GetMapping("/api/stats/{ruleId}/ip")
    @ResponseBody
    public ResponseEntity<List<DetailedRateLimitStats>> getIpStats(
            @PathVariable String ruleId,
            @RequestParam(defaultValue = "50") int limit) {
        List<DetailedRateLimitStats> ipStats = statsService.getIpStats(ruleId, limit);
        return ResponseEntity.ok(ipStats);
    }

    /**
     * 获取用户维度统计信息
     */
    @GetMapping("/api/stats/{ruleId}/user")
    @ResponseBody
    public ResponseEntity<List<DetailedRateLimitStats>> getUserStats(
            @PathVariable String ruleId,
            @RequestParam(defaultValue = "50") int limit) {
        List<DetailedRateLimitStats> userStats = statsService.getUserStats(ruleId, limit);
        return ResponseEntity.ok(userStats);
    }

    /**
     * 获取热点统计（Top访问者）
     * 仅在优化模式下可用
     */
    @GetMapping("/api/stats/{ruleId}/hotspot")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHotspotStats(
            @PathVariable String ruleId,
            @RequestParam(defaultValue = "ip") String dimension,
            @RequestParam(defaultValue = "20") int topN) {
        try {
            if (optimizedStatsService != null) {
                Map<String, Object> hotspotStats = optimizedStatsService.getHotspotStats(ruleId, dimension, topN);
                return ResponseEntity.ok(hotspotStats);
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("error", "热点统计需要启用优化模式");
                result.put("suggestion", "请在配置中设置 rate-limit.stats.optimized=true");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("获取热点统计异常: " + ruleId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取热点统计失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取统计模式信息
     */
    @GetMapping("/api/stats/mode")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatsMode() {
        Map<String, Object> result = new HashMap<>();
        result.put("optimized", optimizedStatsService != null);
        result.put("mode", optimizedStatsService != null ? "优化模式" : "标准模式");
        result.put("description", optimizedStatsService != null ?
            "适用于大量用户场景，使用Hash存储、采样统计和热点统计" :
            "标准统计模式，记录所有详细统计，适用于用户数量较少的场景");

        if (optimizedStatsService != null) {
            result.put("features", Arrays.asList(
                "Hash结构存储减少键数量",
                "采样统计降低存储开销",
                "热点统计关注高频访问者",
                "聚合统计支持趋势分析"
            ));
        } else {
            result.put("features", Arrays.asList(
                "完整的详细统计记录",
                "精确的IP和用户维度统计",
                "实时统计数据"
            ));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取限流记录
     */
    @GetMapping("/api/records/{ruleId}")
    @ResponseBody
    public ResponseEntity<List<RateLimitRecord>> getRateLimitRecords(
            @PathVariable String ruleId,
            @RequestParam(defaultValue = "100") int limit) {
        List<RateLimitRecord> records = statsService.getRateLimitRecords(ruleId, limit);
        return ResponseEntity.ok(records);
    }

    /**
     * 获取最近的限流记录
     */
    @GetMapping("/api/records/recent")
    @ResponseBody
    public ResponseEntity<List<RateLimitRecord>> getRecentRateLimitRecords(
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "100") int limit) {
        List<RateLimitRecord> records = statsService.getRecentRateLimitRecords(minutes, limit);
        return ResponseEntity.ok(records);
    }

    /**
     * 获取趋势数据
     */
    @GetMapping("/api/stats/trend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTrendData(
            @RequestParam(defaultValue = "60") int minutes) {
        Map<String, Object> trendData = statsService.getTrendData(minutes);
        return ResponseEntity.ok(trendData);
    }


    /**
     * 获取趋势数据
     */
    @GetMapping("/api/stats/{ruleId}/trend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRuleTrendData(
            @PathVariable String ruleId,
            @RequestParam(defaultValue = "60") int minutes) {
        Map<String, Object> trendData = statsService.getTrendData(ruleId,minutes);
        return ResponseEntity.ok(trendData);
    }

    /**
     * 生成测试数据
     */
    @PostMapping("/api/test/generate-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateTestData() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 获取所有规则
            List<RateLimitRule> rules = configService.getAllRules();
            if (rules.isEmpty()) {
                result.put("success", false);
                result.put("message", "没有找到限流规则，请先创建规则");
                return ResponseEntity.badRequest().body(result);
            }

            // 为每个规则生成一些测试统计数据
            for (RateLimitRule rule : rules) {
                // 模拟一些请求统计
                for (int i = 0; i < 50; i++) {
                    boolean allowed = Math.random() > 0.2; // 80%通过率
                    statsService.recordRequest(rule.getId(), allowed);
                }
            }

            result.put("success", true);
            result.put("message", "测试数据生成成功");
            result.put("rulesCount", rules.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "生成测试数据失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 调试接口 - 检查当前数据状态
     */
    @GetMapping("/api/debug/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDebugStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            List<RateLimitRule> rules = configService.getAllRules();
            List<RateLimitStats> stats = statsService.getAllStats();
            Map<String, Object> globalStats = statsService.getGlobalStats();

            status.put("rulesCount", rules.size());
            status.put("rules", rules);
            status.put("statsCount", stats.size());
            status.put("stats", stats);
            status.put("globalStats", globalStats);
            status.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            status.put("error", e.getMessage());
            return ResponseEntity.ok(status);
        }
    }

    /**
     * 获取维度概览数据
     */
    @GetMapping("/api/stats/dimension-overview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDimensionOverview() {
        Map<String, Object> overview = new HashMap<>();

        try {
            // 获取所有规则
            List<RateLimitRule> rules = configService.getAllRules();

            // 统计IP维度数据
            Map<String, Object> ipOverview = new HashMap<>();
            int totalIpCount = 0;
            int blockedIpCount = 0;

            // 统计用户维度数据
            Map<String, Object> userOverview = new HashMap<>();
            int totalUserCount = 0;
            int blockedUserCount = 0;

            for (RateLimitRule rule : rules) {
                if (rule.isEnableIpLimit()) {
                    List<DetailedRateLimitStats> ipStats = statsService.getIpStats(rule.getId(), 1000);
                    totalIpCount += ipStats.size();
                    blockedIpCount += (int) ipStats.stream().filter(stat -> stat.getBlockedRequests() > 0).count();
                }

                if (rule.isEnableUserLimit()) {
                    List<DetailedRateLimitStats> userStats = statsService.getUserStats(rule.getId(), 1000);
                    totalUserCount += userStats.size();
                    blockedUserCount += (int) userStats.stream().filter(stat -> stat.getBlockedRequests() > 0).count();
                }
            }

            ipOverview.put("totalCount", totalIpCount);
            ipOverview.put("blockedCount", blockedIpCount);
            ipOverview.put("activeCount", totalIpCount - blockedIpCount);

            userOverview.put("totalCount", totalUserCount);
            userOverview.put("blockedCount", blockedUserCount);
            userOverview.put("activeCount", totalUserCount - blockedUserCount);

            overview.put("ip", ipOverview);
            overview.put("user", userOverview);
            overview.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            overview.put("error", e.getMessage());
            return ResponseEntity.ok(overview);
        }
    }
}
