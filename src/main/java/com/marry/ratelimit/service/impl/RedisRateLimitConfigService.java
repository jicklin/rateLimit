package com.marry.ratelimit.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.service.RateLimitConfigService;
import com.marry.ratelimit.util.RedisKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于Redis的限流配置服务实现
 */
@Service
public class RedisRateLimitConfigService implements RateLimitConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimitConfigService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisKeyGenerator redisKeyGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RateLimitRule saveRule(RateLimitRule rule) {
        try {
            if (rule.getId() == null || rule.getId().trim().isEmpty()) {
                rule.setId(UUID.randomUUID().toString());
            }

            rule.setUpdateTime(System.currentTimeMillis());

            // 保存规则到Redis
            String ruleKey = redisKeyGenerator.generateRuleConfigKey(rule.getId());
            String ruleJson = objectMapper.writeValueAsString(rule);
            redisTemplate.opsForValue().set(ruleKey, ruleJson);

            // 添加到规则列表
            redisTemplate.opsForSet().add(redisKeyGenerator.generateKey(RedisKeyGenerator.RULE_LIST_KEY), rule.getId());

            logger.info("保存限流规则: {}", rule.getName());
            return rule;
        } catch (Exception e) {
            logger.error("保存限流规则异常", e);
            throw new RuntimeException("保存限流规则失败", e);
        }
    }

    @Override
    public RateLimitRule getRule(String ruleId) {
        try {
            String ruleKey = redisKeyGenerator.generateRuleConfigKey(ruleId);
            String ruleJson = (String) redisTemplate.opsForValue().get(ruleKey);

            if (ruleJson != null) {
                return objectMapper.readValue(ruleJson, RateLimitRule.class);
            }

            return null;
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
            // 删除规则配置
            String ruleKey = redisKeyGenerator.generateRuleConfigKey(ruleId);
            redisTemplate.delete(ruleKey);

            // 从规则列表中移除
            redisTemplate.opsForSet().remove(redisKeyGenerator.generateKey(RedisKeyGenerator.RULE_LIST_KEY), ruleId);

            // 删除相关的统计数据
            String statsKey = redisKeyGenerator.generateStatsKey(ruleId);
            redisTemplate.delete(statsKey);

            // 删除相关的令牌桶数据
            redisTemplate.delete(redisTemplate.keys("rate_limit:bucket:" + ruleId + ":*"));

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
