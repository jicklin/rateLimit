package io.github.jicklin.starter.ratelimit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jicklin.starter.ratelimit.model.RateLimitRule;
import io.github.jicklin.starter.ratelimit.service.RateLimitConfigService;
import io.github.jicklin.starter.ratelimit.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 基于Redis的限流配置服务实现
 * 优化版本：添加本地缓存以提高性能
 */
public class RedisRateLimitConfigService implements RateLimitConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitConfigService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyGenerator redisKeyGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 缓存相关
    private volatile List<RateLimitRule> allRulesCache = null;
    private volatile List<RateLimitRule> enabledRulesCache = null;
    private volatile long lastCacheUpdateTime = 0;
    private final long cacheExpireTime = 10000; // 10秒缓存过期时间
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final Map<String, RateLimitRule> singleRuleCache = new ConcurrentHashMap<>();

    public RedisRateLimitConfigService(RedisTemplate<String, Object> redisTemplate,RedisKeyGenerator redisKeyGenerator) {
        this.redisTemplate = redisTemplate;

        this.redisKeyGenerator = redisKeyGenerator;
    }

    @Override
    public RateLimitRule saveRule(RateLimitRule rule) {
        try {
            if (rule.getId() == null || rule.getId().trim().isEmpty()) {
                rule.setId(UUID.randomUUID().toString());
            }

            rule.setUpdateTime(System.currentTimeMillis());

            String key = redisKeyGenerator.generateRuleConfigKey(rule.getId());
            String ruleJson = objectMapper.writeValueAsString(rule);

            redisTemplate.opsForValue().set(key, ruleJson);
            redisTemplate.opsForSet().add(redisKeyGenerator.generateKey(RedisKeyGenerator.RULE_LIST_KEY), rule.getId());

            // 清除缓存
            clearCache();

            logger.info("保存限流规则: {} - {}", rule.getId(), rule.getName());
            return rule;
        } catch (Exception e) {
            logger.error("保存限流规则异常: " + rule.getName(), e);
            throw new RuntimeException("保存限流规则失败", e);
        }
    }

    @Override
    public RateLimitRule getRule(String ruleId) {
        // 先检查单个规则缓存
        RateLimitRule cachedRule = singleRuleCache.get(ruleId);
        if (cachedRule != null) {
            return cachedRule;
        }

        try {
            String key = redisKeyGenerator.generateRuleConfigKey(ruleId);
            Object ruleData = redisTemplate.opsForValue().get(key);

            if (ruleData == null) {
                return null;
            }

            RateLimitRule rule = objectMapper.readValue(ruleData.toString(), RateLimitRule.class);
            // 缓存单个规则
            singleRuleCache.put(ruleId, rule);
            return rule;
        } catch (Exception e) {
            logger.error("获取限流规则异常: " + ruleId, e);
            return null;
        }
    }

    @Override
    public List<RateLimitRule> getAllRules() {
        return loadAllRulesFromRedis();

    }

    @Override
    public List<RateLimitRule> getEnabledRules() {
        // 使用读锁检查启用规则缓存
        cacheLock.readLock().lock();
        try {
            if (enabledRulesCache != null && !isCacheExpired()) {
                return new ArrayList<>(enabledRulesCache);
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        // 缓存过期或不存在，使用写锁重新加载
        cacheLock.writeLock().lock();
        try {
            // 双重检查
            if (enabledRulesCache != null && !isCacheExpired()) {
                return new ArrayList<>(enabledRulesCache);
            }

            // 先获取所有规则（可能会触发allRulesCache的更新）
            List<RateLimitRule> allRules = getAllRules();
            List<RateLimitRule> enabledRules = allRules.stream()
                    .filter(RateLimitRule::isEnabled)
                    .collect(Collectors.toList());

            // 更新启用规则缓存
            enabledRulesCache = enabledRules;
            lastCacheUpdateTime = System.currentTimeMillis();

            return new ArrayList<>(enabledRules);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    @Override
    public void deleteRule(String ruleId) {
        try {
            String key = redisKeyGenerator.generateRuleConfigKey(ruleId);
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(redisKeyGenerator.generateKey(RedisKeyGenerator.RULE_LIST_KEY), ruleId);

            // 删除相关的统计数据
            String statsKey = redisKeyGenerator.generateStatsKey(ruleId);
            redisTemplate.delete(statsKey);

            // 清除缓存
            clearCache();

            logger.info("删除限流规则: {}", ruleId);
        } catch (Exception e) {
            logger.error("删除限流规则异常: " + ruleId, e);
            throw new RuntimeException("删除限流规则失败", e);
        }
    }

    @Override
    public void toggleRule(String ruleId, boolean enabled) {
        try {
            RateLimitRule rule = getRule(ruleId);
            if (rule != null) {
                rule.setEnabled(enabled);
                rule.setUpdateTime(System.currentTimeMillis());
                saveRule(rule); // saveRule 方法会自动清除缓存

                logger.info("切换限流规则状态: {} - {}", ruleId, enabled ? "启用" : "禁用");
            }
        } catch (Exception e) {
            logger.error("切换限流规则状态异常: " + ruleId, e);
            throw new RuntimeException("切换限流规则状态失败", e);
        }
    }

    @Override
    public boolean exists(String ruleId) {
        try {
            return redisTemplate.opsForSet().isMember(redisKeyGenerator.generateKey(RedisKeyGenerator.RULE_LIST_KEY), ruleId);
        } catch (Exception e) {
            logger.error("检查限流规则是否存在异常: " + ruleId, e);
            return false;
        }
    }

    @Override
    public void updatePriority(String ruleId, int priority) {
        try {
            RateLimitRule rule = getRule(ruleId);
            if (rule != null) {
                rule.setPriority(priority);
                rule.setUpdateTime(System.currentTimeMillis());
                saveRule(rule); // saveRule 方法会自动清除缓存

                logger.info("更新限流规则优先级: {} - {}", ruleId, priority);
            }
        } catch (Exception e) {
            logger.error("更新限流规则优先级异常: " + ruleId, e);
            throw new RuntimeException("更新限流规则优先级失败", e);
        }
    }

    /**
     * 从Redis批量加载所有规则
     */
    private List<RateLimitRule> loadAllRulesFromRedis() {
        try {
            List<RateLimitRule> rules = new ArrayList<>();

            // 获取所有规则ID
            Set<Object> ruleIds = redisTemplate.opsForSet().members(redisKeyGenerator.generateKey(RedisKeyGenerator.RULE_LIST_KEY));
            if (ruleIds != null && !ruleIds.isEmpty()) {
                // 批量获取规则数据
                List<String> keys = ruleIds.stream()
                        .map(ruleId -> redisKeyGenerator.generateRuleConfigKey(ruleId.toString()))
                        .collect(Collectors.toList());

                List<Object> ruleDataList = redisTemplate.opsForValue().multiGet(keys);

                // 解析规则数据
                for (int i = 0; i < ruleDataList.size(); i++) {
                    Object ruleData = ruleDataList.get(i);
                    if (ruleData != null) {
                        try {
                            RateLimitRule rule = objectMapper.readValue(ruleData.toString(), RateLimitRule.class);
                            rules.add(rule);
                            // 同时更新单个规则缓存
                            singleRuleCache.put(rule.getId(), rule);
                        } catch (Exception e) {
                            logger.warn("解析限流规则失败: {}", keys.get(i), e);
                        }
                    }
                }
            }

            // 按优先级排序
            return rules.stream()
                    .sorted(Comparator.comparingInt(RateLimitRule::getPriority))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("从Redis加载所有限流规则异常", e);
            return new ArrayList<>();
        }
    }

    /**
     * 检查缓存是否过期
     */
    private boolean isCacheExpired() {
        return System.currentTimeMillis() - lastCacheUpdateTime > cacheExpireTime;
    }

    /**
     * 清除所有缓存
     */
    private void clearCache() {
        cacheLock.writeLock().lock();
        try {
            allRulesCache = null;
            enabledRulesCache = null;
            singleRuleCache.clear();
            lastCacheUpdateTime = 0;
            logger.debug("已清除限流规则缓存");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
}
