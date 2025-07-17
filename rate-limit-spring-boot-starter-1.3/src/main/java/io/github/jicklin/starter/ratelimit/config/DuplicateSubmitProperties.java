package io.github.jicklin.starter.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 防重复提交配置属性
 *
 * @author marry
 */
@ConfigurationProperties(prefix = "rate-limit.duplicate-submit")
public class DuplicateSubmitProperties {

    /**
     * 是否启用防重复提交功能
     */
    private boolean enabled = true;

    /**
     * 默认防重复提交间隔（秒）
     */
    private long defaultInterval = 5;

    /**
     * 默认提示信息
     */
    private String defaultMessage = "请勿重复提交";

    /**
     * Redis key前缀
     */
    private String keyPrefix = "duplicate_submit";

    /**
     * 是否启用性能监控
     */
    private boolean enableMetrics = false;

    /**
     * 是否启用详细日志
     */
    private boolean enableDetailLog = false;

    /**
     * 锁值生成策略
     */
    private LockValueStrategy lockValueStrategy = LockValueStrategy.THREAD_TIME_NANO;

    /**
     * 参数处理配置
     */
    private ParamConfig param = new ParamConfig();

    /**
     * 用户标识提取配置
     */
    private UserConfig user = new UserConfig();

    /**
     * 性能配置
     */
    private PerformanceConfig performance = new PerformanceConfig();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDefaultInterval() {
        return defaultInterval;
    }

    public void setDefaultInterval(long defaultInterval) {
        this.defaultInterval = defaultInterval;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public void setDefaultMessage(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    public boolean isEnableDetailLog() {
        return enableDetailLog;
    }

    public void setEnableDetailLog(boolean enableDetailLog) {
        this.enableDetailLog = enableDetailLog;
    }

    public LockValueStrategy getLockValueStrategy() {
        return lockValueStrategy;
    }

    public void setLockValueStrategy(LockValueStrategy lockValueStrategy) {
        this.lockValueStrategy = lockValueStrategy;
    }

    public ParamConfig getParam() {
        return param;
    }

    public void setParam(ParamConfig param) {
        this.param = param;
    }

    public UserConfig getUser() {
        return user;
    }

    public void setUser(UserConfig user) {
        this.user = user;
    }

    public PerformanceConfig getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceConfig performance) {
        this.performance = performance;
    }

    /**
     * 锁值生成策略
     */
    public enum LockValueStrategy {
        /**
         * 线程ID + 时间戳 + 纳秒时间
         */
        THREAD_TIME_NANO,

        /**
         * UUID
         */
        UUID,

        /**
         * 线程ID + 时间戳
         */
        THREAD_TIME,

        /**
         * 自定义
         */
        CUSTOM
    }

    /**
     * 参数处理配置
     */
    public static class ParamConfig {
        /**
         * 最大参数深度（对象属性提取）
         */
        private int maxDepth = 3;

        /**
         * 是否缓存参数哈希
         */
        private boolean cacheHash = true;

        /**
         * 参数序列化最大长度
         */
        private int maxSerializedLength = 1024;

        /**
         * 是否忽略null值参数
         */
        private boolean ignoreNullValues = true;

        // Getters and Setters
        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public boolean isCacheHash() {
            return cacheHash;
        }

        public void setCacheHash(boolean cacheHash) {
            this.cacheHash = cacheHash;
        }

        public int getMaxSerializedLength() {
            return maxSerializedLength;
        }

        public void setMaxSerializedLength(int maxSerializedLength) {
            this.maxSerializedLength = maxSerializedLength;
        }

        public boolean isIgnoreNullValues() {
            return ignoreNullValues;
        }

        public void setIgnoreNullValues(boolean ignoreNullValues) {
            this.ignoreNullValues = ignoreNullValues;
        }
    }

    /**
     * 用户标识提取配置
     */
    public static class UserConfig {
        /**
         * 用户标识提取顺序
         */
        private String[] extractOrder = {"authorization", "userId", "session", "ip"};

        /**
         * Authorization header名称
         */
        private String authorizationHeader = "Authorization";

        /**
         * 用户ID参数名
         */
        private String userIdParam = "userId";

        /**
         * Session中用户信息的key
         */
        private String sessionUserKey = "user";

        /**
         * 是否使用IP作为最后的fallback
         */
        private boolean useIpFallback = true;

        // Getters and Setters
        public String[] getExtractOrder() {
            return extractOrder;
        }

        public void setExtractOrder(String[] extractOrder) {
            this.extractOrder = extractOrder;
        }

        public String getAuthorizationHeader() {
            return authorizationHeader;
        }

        public void setAuthorizationHeader(String authorizationHeader) {
            this.authorizationHeader = authorizationHeader;
        }

        public String getUserIdParam() {
            return userIdParam;
        }

        public void setUserIdParam(String userIdParam) {
            this.userIdParam = userIdParam;
        }

        public String getSessionUserKey() {
            return sessionUserKey;
        }

        public void setSessionUserKey(String sessionUserKey) {
            this.sessionUserKey = sessionUserKey;
        }

        public boolean isUseIpFallback() {
            return useIpFallback;
        }

        public void setUseIpFallback(boolean useIpFallback) {
            this.useIpFallback = useIpFallback;
        }
    }

    /**
     * 性能配置
     */
    public static class PerformanceConfig {
        /**
         * Key生成超时告警阈值（毫秒）
         */
        private long keyGenerationWarnThreshold = 10;

        /**
         * Redis操作超时告警阈值（毫秒）
         */
        private long redisOperationWarnThreshold = 50;

        /**
         * 是否启用Key生成缓存
         */
        private boolean enableKeyCache = false;

        /**
         * Key缓存大小
         */
        private int keyCacheSize = 1000;

        /**
         * Key缓存过期时间（秒）
         */
        private int keyCacheExpireSeconds = 60;

        // Getters and Setters
        public long getKeyGenerationWarnThreshold() {
            return keyGenerationWarnThreshold;
        }

        public void setKeyGenerationWarnThreshold(long keyGenerationWarnThreshold) {
            this.keyGenerationWarnThreshold = keyGenerationWarnThreshold;
        }

        public long getRedisOperationWarnThreshold() {
            return redisOperationWarnThreshold;
        }

        public void setRedisOperationWarnThreshold(long redisOperationWarnThreshold) {
            this.redisOperationWarnThreshold = redisOperationWarnThreshold;
        }

        public boolean isEnableKeyCache() {
            return enableKeyCache;
        }

        public void setEnableKeyCache(boolean enableKeyCache) {
            this.enableKeyCache = enableKeyCache;
        }

        public int getKeyCacheSize() {
            return keyCacheSize;
        }

        public void setKeyCacheSize(int keyCacheSize) {
            this.keyCacheSize = keyCacheSize;
        }

        public int getKeyCacheExpireSeconds() {
            return keyCacheExpireSeconds;
        }

        public void setKeyCacheExpireSeconds(int keyCacheExpireSeconds) {
            this.keyCacheExpireSeconds = keyCacheExpireSeconds;
        }
    }

    @Override
    public String toString() {
        return String.format("DuplicateSubmitProperties{enabled=%s, defaultInterval=%d, keyPrefix='%s'}",
            enabled, defaultInterval, keyPrefix);
    }
}
