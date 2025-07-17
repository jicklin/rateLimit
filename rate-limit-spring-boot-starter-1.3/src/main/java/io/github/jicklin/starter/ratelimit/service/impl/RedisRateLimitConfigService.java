package io.github.jicklin.starter.ratelimit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jicklin.starter.ratelimit.model.RateLimitRule;
import io.github.jicklin.starter.ratelimit.service.RateLimitConfigService;
import io.github.jicklin.starter.ratelimit.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于Redis的限流配置服务实现
 */
public class RedisRateLimitConfigService implements RateLimitConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitConfigService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyGenerator redisKeyGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            logger.info("保存限流规则: {} - {}", rule.getId(), rule.getName());
            return rule;
        } catch (Exception e) {
            logger.error("保存限流规则异常: " + rule.getName(), e);
            throw new RuntimeException("保存限流规则失败", e);
        }
    }

    @Override
    public RateLimitRule getRule(String ruleId) {
        try {
            String key = redisKeyGenerator.generateRuleConfigKey(ruleId);
            Object ruleData = redisTemplate.opsForValue().get(key);

            if (ruleData == null) {
                return null;
            }

            return objectMapper.readValue(ruleData.toString(), RateLimitRule.class);
        } catch (Exception e) {
            logger.error("获取限流规则异常: " + ruleId, e);
            return null;
        }
    }

    @Override
    public List<RateLimitRule> getAllRules() {
        try {
            List<RateLimitRule> rules = new ArrayList<>();

            // 获取所有规则ID
            Object ruleIds = redisTemplate.opsForSet().members(redisKeyGenerator.generateKey(RedisKeyGenerator.RULE_LIST_KEY));
            if (ruleIds != null) {
                for (Object ruleId : (Set)ruleIds) {
                    RateLimitRule rule = getRule(ruleId.toString());
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }

            // 按优先级排序
            return rules.stream()
                    .sorted(Comparator.comparingInt(RateLimitRule::getPriority))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取所有限流规则异常", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<RateLimitRule> getEnabledRules() {
        return getAllRules().stream()
                .filter(RateLimitRule::isEnabled)
                .collect(Collectors.toList());
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
                saveRule(rule);

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
                saveRule(rule);

                logger.info("更新限流规则优先级: {} - {}", ruleId, priority);
            }
        } catch (Exception e) {
            logger.error("更新限流规则优先级异常: " + ruleId, e);
            throw new RuntimeException("更新限流规则优先级失败", e);
        }
    }
}
