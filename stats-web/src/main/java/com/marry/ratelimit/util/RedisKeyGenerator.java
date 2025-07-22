package com.marry.ratelimit.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Redis键生成工具类
 */
@Service
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
    @Value("${rate-limit.redis-key-prefix:default}")
    private String redisKeyPrefix;


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
     * 生成详细统计Hash键（优化版本，减少键数量）
     * 使用Hash结构，一个规则的所有IP/用户统计存储在一个Hash中
     * 推荐在高并发场景使用此方法
     *
     * @param ruleId 规则ID
     * @param dimension 维度（ip、user）
     * @return Redis Hash键
     */
    public String generateDetailedStatsHashKey(String ruleId, String dimension) {
        return redisKeyPrefix + ":stats_hash:" + ruleId + ":" + dimension;
    }

    /**
     * 生成详细统计键（支持IP和用户维度）
     * @deprecated 在高并发场景建议使用 generateDetailedStatsHashKey 以减少键数量
     *
     * @param ruleId 规则ID
     * @param dimension 维度（ip、user）
     * @param dimensionValue 维度值
     * @return Redis键
     */
    @Deprecated
    public String generateDetailedStatsKey(String ruleId, String dimension, String dimensionValue) {
        return redisKeyPrefix + ":detailed_stats:" + ruleId + ":" + dimension + ":" + dimensionValue;
    }

    /**
     * 生成维度统计Set键（优化版本）
     * 使用Set结构存储活跃的IP/用户列表，避免大量单独的键
     *
     * @param ruleId 规则ID
     * @param dimension 维度（ip、user）
     * @return Redis Set键
     */
    public String generateDimensionSetKey(String ruleId, String dimension) {
        return redisKeyPrefix + ":dimension_set:" + ruleId + ":" + dimension;
    }

    /**
     * 生成维度统计列表键
     * @deprecated 建议使用 generateDimensionSetKey
     *
     * @param ruleId 规则ID
     * @param dimension 维度（ip、user）
     * @return Redis键
     */
    @Deprecated
    public String generateDimensionListKey(String ruleId, String dimension) {
        return redisKeyPrefix + ":dimension_list:" + ruleId + ":" + dimension;
    }

    /**
     * 生成采样统计键（用于大量用户场景）
     * 当参与人数过多时，可以使用采样统计减少存储开销
     *
     * @param ruleId 规则ID
     * @param dimension 维度
     * @param sampleRate 采样率（如100表示1%采样）
     * @return Redis键
     */
    public String generateSampledStatsKey(String ruleId, String dimension, int sampleRate) {
        return redisKeyPrefix + ":sampled_stats:" + ruleId + ":" + dimension + ":" + sampleRate;
    }

    /**
     * 生成聚合统计键（按时间窗口聚合）
     * 用于减少长期存储的统计数据量
     *
     * @param ruleId 规则ID
     * @param dimension 维度
     * @param timeWindow 时间窗口（分钟）
     * @return Redis键
     */
    public String generateAggregatedStatsKey(String ruleId, String dimension, long timeWindow) {
        long windowStart = (System.currentTimeMillis() / (timeWindow * 60 * 1000L)) * (timeWindow * 60 * 1000L);
        return redisKeyPrefix + ":agg_stats:" + ruleId + ":" + dimension + ":" + windowStart;
    }

    /**
     * 生成热点统计键（只记录访问频率高的IP/用户）
     * 使用ZSet结构，按访问次数排序，只保留Top N
     *
     * @param ruleId 规则ID
     * @param dimension 维度
     * @return Redis ZSet键
     */
    public String generateHotspotStatsKey(String ruleId, String dimension) {
        return redisKeyPrefix + ":hotspot_stats:" + ruleId + ":" + dimension;
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
