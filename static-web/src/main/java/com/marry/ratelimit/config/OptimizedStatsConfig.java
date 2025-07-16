package com.marry.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 优化统计配置
 */
@Component
@ConfigurationProperties(prefix = "rate-limit.stats")
public class OptimizedStatsConfig {
    
    /**
     * 是否启用优化统计
     */
    private boolean optimized = false;
    
    /**
     * 采样率（例如：100表示1%采样率）
     */
    private int sampleRate = 100;
    
    /**
     * 热点统计保留Top N
     */
    private int hotspotTopN = 1000;
    
    /**
     * 聚合窗口大小（分钟）
     */
    private int aggregationWindowMinutes = 5;

    public boolean isOptimized() {
        return optimized;
    }

    public void setOptimized(boolean optimized) {
        this.optimized = optimized;
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
