package com.marry.starter.ratelimit.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 限流配置属性
 */
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /**
     * 是否启用限流功能
     */
    private boolean enabled = true;

    /**
     * Redis键前缀
     */
    private String redisKeyPrefix = "";

    /**
     * 默认令牌桶容量
     */
    private int defaultBucketCapacity = 10;

    /**
     * 默认令牌补充速率（每秒）
     */
    private int defaultRefillRate = 5;

    /**
     * 默认时间窗口（秒）
     */
    private int defaultTimeWindow = 1;

    /**
     * 拦截器配置
     */
    private InterceptorConfig interceptor = new InterceptorConfig();

    /**
     * 统计配置
     */
    private StatsConfig stats = new StatsConfig();

    /**
     * 管理界面配置
     */
    private AdminConfig admin = new AdminConfig();

    public static class InterceptorConfig {
        /**
         * 是否启用拦截器
         */
        private boolean enabled = true;

        /**
         * 拦截路径模式
         */
        private List<String> pathPatterns = new ArrayList<>();

        /**
         * 排除路径模式
         */
        private List<String> excludePathPatterns = new ArrayList<>();

        /**
         * 拦截器顺序
         */
        private int order = 0;

        public InterceptorConfig() {
            // 默认拦截所有路径
            pathPatterns.add("/**");
            // 默认排除静态资源和管理接口
            excludePathPatterns.add("/static/**");
            excludePathPatterns.add("/css/**");
            excludePathPatterns.add("/js/**");
            excludePathPatterns.add("/images/**");
            excludePathPatterns.add("/favicon.ico");
            excludePathPatterns.add("/ratelimit/**");
        }

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getPathPatterns() {
            return pathPatterns;
        }

        public void setPathPatterns(List<String> pathPatterns) {
            this.pathPatterns = pathPatterns;
        }

        public List<String> getExcludePathPatterns() {
            return excludePathPatterns;
        }

        public void setExcludePathPatterns(List<String> excludePathPatterns) {
            this.excludePathPatterns = excludePathPatterns;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }
    }

    public static class StatsConfig {
        /**
         * 是否启用统计功能
         */
        private boolean enabled = true;


        private boolean enableDetail = false;

        /**
         * 是否启用优化模式（适用于大量用户场景）
         * 当参与人数过多时，启用此模式可以减少Redis键数量
         */
        private boolean optimized = false;

        /**
         * 统计数据保留时间（小时）
         */
        private int retentionHours = 24;

        /**
         * 实时统计时间窗口（分钟）
         */
        private int realtimeWindowMinutes = 15;

        /**
         * 最大详细统计数量（优化模式下使用）
         * 当IP或用户数量超过此值时，自动切换到采样统计
         */
        private int maxDetailedStats = 10000;

        /**
         * 采样率（优化模式下使用）
         * 如100表示1%采样，即每100个请求记录1个
         */
        private int sampleRate = 100;

        /**
         * 热点统计保留数量（优化模式下使用）
         * 只保留访问频率最高的Top N个IP/用户
         */
        private int hotspotTopN = 1000;

        /**
         * 聚合统计时间窗口（分钟，优化模式下使用）
         * 将统计数据按时间窗口聚合，减少存储空间
         */
        private int aggregationWindowMinutes = 5;

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRetentionHours() {
            return retentionHours;
        }

        public void setRetentionHours(int retentionHours) {
            this.retentionHours = retentionHours;
        }

        public int getRealtimeWindowMinutes() {
            return realtimeWindowMinutes;
        }

        public void setRealtimeWindowMinutes(int realtimeWindowMinutes) {
            this.realtimeWindowMinutes = realtimeWindowMinutes;
        }

        public boolean isEnableDetail() {
            return enableDetail;
        }

        public void setEnableDetail(boolean enableDetail) {
            this.enableDetail = enableDetail;
        }

        public boolean isOptimized() {
            return optimized;
        }

        public void setOptimized(boolean optimized) {
            this.optimized = optimized;
        }

        public int getMaxDetailedStats() {
            return maxDetailedStats;
        }

        public void setMaxDetailedStats(int maxDetailedStats) {
            this.maxDetailedStats = maxDetailedStats;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        public int getHotspotTopN() {
            return hotspotTopN;
        }

        public void setHotspotTopN(int hotspotTopN) {
            this.hotspotTopN = hotspotTopN;
        }

        public int getAggregationWindowMinutes() {
            return aggregationWindowMinutes;
        }

        public void setAggregationWindowMinutes(int aggregationWindowMinutes) {
            this.aggregationWindowMinutes = aggregationWindowMinutes;
        }
    }

    public static class AdminConfig {
        /**
         * 是否启用管理界面
         */
        private boolean enabled = false;

        /**
         * 管理界面路径前缀
         */
        private String pathPrefix = "/ratelimit";

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }
    }

    // Main class getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public int getDefaultBucketCapacity() {
        return defaultBucketCapacity;
    }

    public void setDefaultBucketCapacity(int defaultBucketCapacity) {
        this.defaultBucketCapacity = defaultBucketCapacity;
    }

    public int getDefaultRefillRate() {
        return defaultRefillRate;
    }

    public void setDefaultRefillRate(int defaultRefillRate) {
        this.defaultRefillRate = defaultRefillRate;
    }

    public int getDefaultTimeWindow() {
        return defaultTimeWindow;
    }

    public void setDefaultTimeWindow(int defaultTimeWindow) {
        this.defaultTimeWindow = defaultTimeWindow;
    }

    public InterceptorConfig getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(InterceptorConfig interceptor) {
        this.interceptor = interceptor;
    }

    public StatsConfig getStats() {
        return stats;
    }

    public void setStats(StatsConfig stats) {
        this.stats = stats;
    }

    public AdminConfig getAdmin() {
        return admin;
    }

    public void setAdmin(AdminConfig admin) {
        this.admin = admin;
    }
}
