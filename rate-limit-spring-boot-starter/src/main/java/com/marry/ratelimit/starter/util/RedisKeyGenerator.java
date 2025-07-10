package com.marry.ratelimit.starter.util;

/**
 * Redis键生成工具类
 */
public class RedisKeyGenerator {

    /**
     * 限流规则配置键前缀
     */
    public static final String RULE_CONFIG_PREFIX = "rate_limit:config:rule:";

    /**
     * 限流规则列表键
     */
    public static final String RULE_LIST_KEY = "rate_limit:config:rules";

    /**
     * 限流统计键前缀
     */
    public static final String STATS_PREFIX = "rate_limit:stats:";

    /**
     * 令牌桶键前缀
     */
    public static final String BUCKET_PREFIX = "rate_limit:bucket:";
    /**
     * Redis键前缀
     */
    private String redisKeyPrefix = "xports";

    public RedisKeyGenerator(String redisKeyPrefix) {
        if (redisKeyPrefix != null && redisKeyPrefix.trim().isEmpty()) {
            this.redisKeyPrefix = redisKeyPrefix;
        }
    }

    /**
     * 生成规则配置键
     *
     * @param ruleId 规则ID
     * @return Redis键
     */
    public  String generateRuleConfigKey(String ruleId) {
        return redisKeyPrefix + ":" + RULE_CONFIG_PREFIX + ruleId;
    }

    /**
     * 生成统计键
     *
     * @param ruleId 规则ID
     * @return Redis键
     */
    public  String generateStatsKey(String ruleId) {
        return redisKeyPrefix+ ":" + STATS_PREFIX + ruleId;
    }

    /**
     * 生成令牌桶键
     *
     * @param ruleId 规则ID
     * @param identifier 标识符（IP、用户ID等）
     * @return Redis键
     */
    public  String generateBucketKey(String ruleId, String identifier) {
        return redisKeyPrefix+ ":" + BUCKET_PREFIX + ruleId + ":" + identifier;
    }

    /**
     * 生成令牌桶键（带时间窗口）
     *
     * @param ruleId 规则ID
     * @param identifier 标识符
     * @param timeWindow 时间窗口（秒）
     * @return Redis键
     */
    public String generateBucketKeyWithWindow(String ruleId, String identifier, int timeWindow) {
        long currentWindow = System.currentTimeMillis() / (timeWindow * 1000L);
        return redisKeyPrefix+ ":" + BUCKET_PREFIX + ruleId + ":" + identifier + ":" + currentWindow;
    }

    /**
     * 生成全局统计键
     *
     * @return Redis键
     */
    public  String generateGlobalStatsKey() {
        return redisKeyPrefix+ ":stats:global";
    }

    /**
     * 生成规则启用状态键
     *
     * @param ruleId 规则ID
     * @return Redis键
     */
    public  String generateRuleEnabledKey(String ruleId) {
        return redisKeyPrefix + ":enabled:" + ruleId;
    }

    /**
     * 生成详细统计键（支持IP和用户维度）
     *
     * @param ruleId 规则ID
     * @param dimension 维度（ip、user）
     * @param dimensionValue 维度值
     * @return Redis键
     */
    public  String generateDetailedStatsKey(String ruleId, String dimension, String dimensionValue) {
        return redisKeyPrefix+ ":detailed_stats:" + ruleId + ":" + dimension + ":" + dimensionValue;
    }

    /**
     * 生成维度统计列表键
     *
     * @param ruleId 规则ID
     * @param dimension 维度（ip、user）
     * @return Redis键
     */
    public  String generateDimensionListKey(String ruleId, String dimension) {
        return redisKeyPrefix + ":" + "rate_limit:dimension_list:" + ruleId + ":" + dimension;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }


    public String generateKey(String key) {
        return redisKeyPrefix + ":" + key;
    }

    public String generateRecordKey(String ruleId, String id) {
        return redisKeyPrefix + ":" + "rate_limit:records:" + ruleId + ":" + id;
    }

    public String generateTimeIndexKey(String ruleId) {
        return redisKeyPrefix + ":" + "rate_limit:time_index:" + ruleId;
    }

    public String generateGlobalTimeIndexKey() {


        return redisKeyPrefix + ":" + "rate_limit:global_time_index";
    }

    public String generateRealtimeKey(String ruleId, long currentMinute) {
        return redisKeyPrefix + ":" + "rate_limit:realtime:" + ruleId + ":" + currentMinute;

    }
}
