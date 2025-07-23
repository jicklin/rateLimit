package io.github.jicklin.starter.ratelimit.service.impl;

import io.github.jicklin.starter.ratelimit.annotation.DuplicateSubmitIgnore;
import io.github.jicklin.starter.ratelimit.annotation.DuplicateSubmitParam;
import io.github.jicklin.starter.ratelimit.annotation.PreventDuplicateSubmit;
import io.github.jicklin.starter.ratelimit.model.ParamGroupInfo;
import io.github.jicklin.starter.ratelimit.processor.DefaultProcessContext;
import io.github.jicklin.starter.ratelimit.processor.ParamValueProcessor;
import io.github.jicklin.starter.ratelimit.processor.ParamValueProcessorManager;
import io.github.jicklin.starter.ratelimit.service.DuplicateSubmitService;
import io.github.jicklin.starter.ratelimit.strategy.UserIdentifierExtractor;
import io.github.jicklin.starter.ratelimit.util.GroupNameGenerator;
import io.github.jicklin.starter.ratelimit.util.ParameterValueExtractor;
import io.github.jicklin.starter.ratelimit.util.RedisKeyGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于Redis的防重复提交服务实现
 *
 * @author marry
 */
@Service
public class RedisDuplicateSubmitService implements DuplicateSubmitService {

    private static final Logger logger = LoggerFactory.getLogger(RedisDuplicateSubmitService.class);



    /**
     * Lua脚本：使用SETNX原子性地检查和设置防重复提交key
     * 返回值：
     * - 0: 设置成功，不是重复提交
     * - 剩余TTL（毫秒）: 如果是重复提交，返回剩余时间
     */
    private static final String LUA_SCRIPT_CHECK_AND_SET =
        "local key = KEYS[1]\n" +
        "local value = ARGV[1]\n" +
        "local ttlStr = ARGV[2]\n" +
        "-- 直接使用字符串形式的TTL，让Redis自己转换\n" +
        "-- 使用SETNX尝试设置key\n" +
        "local result = redis.call('SETNX', key, value)\n" +
        "if result == 1 then\n" +
        "    -- 设置成功，设置过期时间（Redis会自动转换字符串为数字）\n" +
        "    redis.call('PEXPIRE', key, ttlStr)\n" +
        "    return 0\n" +
        "else\n" +
        "    -- key已存在，返回剩余TTL\n" +
        "    local remaining = redis.call('PTTL', key)\n" +
        "    return remaining > 0 and remaining or 1\n" +
        "end";

    /**
     * Lua脚本：获取key的剩余TTL
     */
    private static final String LUA_SCRIPT_GET_TTL =
        "local key = KEYS[1]\n" +
        "local ttl = redis.call('PTTL', key)\n" +
        "return ttl > 0 and ttl or 0";

    /**
     * Lua脚本：安全删除锁（只删除value匹配的锁）
     * 返回值：
     * - 1: 删除成功
     * - 0: key不存在或value不匹配
     */
    private static final String LUA_SCRIPT_SAFE_DELETE =
        "local key = KEYS[1]\n" +
        "local value = ARGV[1]\n" +
        "local current = redis.call('GET', key)\n" +
        "if current == value then\n" +
        "    return redis.call('DEL', key)\n" +
        "else\n" +
        "    return 0\n" +
        "end";

    private final DefaultRedisScript<Long> checkAndSetScript;
    private final DefaultRedisScript<Long> getTtlScript;
    private final DefaultRedisScript<Long> safeDeleteScript;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisKeyGenerator keyGenerator;

    @Autowired
    private List<UserIdentifierExtractor> userIdentifierExtractors;

    @Autowired
    private ParameterValueExtractor parameterValueExtractor;

    @Autowired
    private ParamValueProcessorManager processorManager;

    public RedisDuplicateSubmitService() {
        // 初始化Lua脚本
        this.checkAndSetScript = new DefaultRedisScript<>();
        this.checkAndSetScript.setScriptText(LUA_SCRIPT_CHECK_AND_SET);
        this.checkAndSetScript.setResultType(Long.class);

        this.getTtlScript = new DefaultRedisScript<>();
        this.getTtlScript.setScriptText(LUA_SCRIPT_GET_TTL);
        this.getTtlScript.setResultType(Long.class);

        this.safeDeleteScript = new DefaultRedisScript<>();
        this.safeDeleteScript.setScriptText(LUA_SCRIPT_SAFE_DELETE);
        this.safeDeleteScript.setResultType(Long.class);
    }



    @Override
    public void recordSubmit(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        // 使用SETNX的方案中，记录操作已经在isDuplicateSubmit中完成
        // 这个方法保留为空实现，保持接口兼容性
        logger.debug("使用SETNX方案，记录操作已在检查时完成");
    }


    /**
     * 公开的key生成方法，供AOP切面调用
     */
    public String generateKey(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        StringBuilder keyBuilder = new StringBuilder();

        keyBuilder.append("duplicate_submit:");

        // 添加前缀
        if (!annotation.keyPrefix().isEmpty()) {
            keyBuilder.append(annotation.keyPrefix()).append(":");
        } else {

            // 添加方法标识
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            keyBuilder.append(method.getDeclaringClass().getSimpleName())
                    .append(".")
                    .append(method.getName());
        }



        // 添加用户标识
        if (annotation.includeUser()) {
            String userIdentifier = extractUserIdentifier(request);
            if (userIdentifier != null) {
                keyBuilder.append(":user:").append(userIdentifier);
            }
        }

        // 添加参数标识
        if (annotation.paramStrategy() != PreventDuplicateSubmit.ParamStrategy.EXCLUDE_ALL) {
            String paramsHash = generateParamsHash(joinPoint, request, annotation);
            if (paramsHash != null) {
                keyBuilder.append(":params:").append(paramsHash);
            }
        }

        return keyBuilder.toString();
    }



    /**
     * 提取用户标识
     */
    private String extractUserIdentifier(HttpServletRequest request) {
        // 按优先级排序用户标识提取器
        userIdentifierExtractors.sort(Comparator.comparingInt(UserIdentifierExtractor::getOrder));

        for (UserIdentifierExtractor extractor : userIdentifierExtractors) {
            try {
                String userIdentifier = extractor.extractUserIdentifier(request);
                if (userIdentifier != null && !userIdentifier.isEmpty()) {
                    return userIdentifier;
                }
            } catch (Exception e) {
                logger.warn("用户标识提取器执行失败: {}", extractor.getClass().getSimpleName(), e);
            }
        }

        return null;
    }

    /**
     * 生成参数哈希值
     */
    private String generateParamsHash(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        try {
            Map<String, Object> params = new TreeMap<>(); // 使用TreeMap保证顺序一致

            // 获取方法参数
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Parameter[] parameters = signature.getMethod().getParameters();
            Object[] args = joinPoint.getArgs();

            // 处理方法参数
            String userIdentifier = extractUserIdentifier(request);
            processMethodParameters(params, parameters, args, annotation, signature, request, userIdentifier);

            // 处理请求参数（只在INCLUDE_ALL和EXCLUDE_ANNOTATED策略下）
            if (annotation.paramStrategy() == PreventDuplicateSubmit.ParamStrategy.INCLUDE_ALL ||
                annotation.paramStrategy() == PreventDuplicateSubmit.ParamStrategy.EXCLUDE_ANNOTATED) {
                processRequestParameters(params, request);
            }

            if (params.isEmpty()) {
                return null;
            }

            // 生成MD5哈希
            String paramsString = params.toString();
            return DigestUtils.md5DigestAsHex(paramsString.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            logger.warn("生成参数哈希失败", e);
            return null;
        }
    }

    /**
     * 处理方法参数
     */
    private void processMethodParameters(Map<String, Object> params, Parameter[] parameters, Object[] args, PreventDuplicateSubmit annotation, MethodSignature signature, HttpServletRequest request, String userIdentifier) {
        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];
            String parameterName = parameterNames[i];

            // 跳过特殊参数
            if (isSpecialParameter(arg)) {
                continue;
            }

            // 获取参数注解信息
            ParameterAnnotationInfo annotationInfo = getParameterAnnotationInfo(signature, i);

            // 根据策略处理参数
            if (shouldIncludeParameterWithInfo(annotationInfo, annotation)) {
                String paramName = getParameterNameWithInfo(parameter, i, annotationInfo, parameterName);

                // 创建处理上下文
                DefaultProcessContext context = new DefaultProcessContext(null, request, i, userIdentifier);
                context.setAttribute("signature", signature);
                context.setAttribute("allArgs", args);

                Object paramValue = extractParameterValueWithInfo(arg, annotationInfo, paramName, context);

                // 处理集合类型的参数值
                if (processorManager.isCollection(paramValue)) {
                    // 集合中的每个元素作为独立的参数
                    java.util.List<Object> valueList = processorManager.toList(paramValue);
                    for (int j = 0; j < valueList.size(); j++) {
                        Object element = valueList.get(j);

                        // 生成基于内容的稳定参数名称
                        String stableParamName = GroupNameGenerator.generateStableParamName(paramName, element);

                        params.put(stableParamName, parameterValueExtractor.safeToString(element));
                    }
                } else {
                    // 单个值
                    params.put(paramName, parameterValueExtractor.safeToString(paramValue));
                }
            }
        }
    }

    /**
     * 处理请求参数
     */
    private void processRequestParameters(Map<String, Object> params, HttpServletRequest request) {
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String[] values = request.getParameterValues(paramName);
            if (values.length == 1) {
                params.put("req_" + paramName, values[0]);
            } else {
                params.put("req_" + paramName, Arrays.asList(values));
            }
        }
    }

    /**
     * 判断是否为特殊参数
     */
    private boolean isSpecialParameter(Object arg) {
        return arg instanceof HttpServletRequest ||
                arg instanceof javax.servlet.http.HttpServletResponse ||
                arg instanceof javax.servlet.http.HttpSession
                || arg instanceof MultipartFile
                || arg instanceof MultipartFile[]
                || arg instanceof ModelMap
                || arg instanceof Model
                || arg instanceof byte[];

    }





    /**
     * 基于注解信息判断是否应该包含此参数
     */
    private boolean shouldIncludeParameterWithInfo(ParameterAnnotationInfo annotationInfo, PreventDuplicateSubmit annotation) {
        PreventDuplicateSubmit.ParamStrategy strategy = annotation.paramStrategy();

        switch (strategy) {
            case INCLUDE_ALL:
                // 包含所有参数，除非被@DuplicateSubmitIgnore标注
                return !annotationInfo.hasIgnore();

            case INCLUDE_ANNOTATED:
                // 只包含被@DuplicateSubmitParam(include=true)标注的参数
                DuplicateSubmitParam paramAnnotation = annotationInfo.getParamAnnotation();
                return paramAnnotation != null && paramAnnotation.include();

            case EXCLUDE_ANNOTATED:
                // 排除被@DuplicateSubmitIgnore标注的参数
                return !annotationInfo.hasIgnore();

            case EXCLUDE_ALL:
                // 不包含任何参数
                return false;

            default:
                return true;
        }
    }

    /**
     * 基于注解信息获取参数名称
     */
    private String getParameterNameWithInfo(Parameter parameter, int paramIndex, ParameterAnnotationInfo annotationInfo, String parameterName) {
        // 优先使用@DuplicateSubmitParam中的alias
        DuplicateSubmitParam paramAnnotation = annotationInfo.getParamAnnotation();
        if (paramAnnotation != null && !paramAnnotation.alias().isEmpty()) {
            return paramAnnotation.alias();
        }
        if(parameterName!=null && !parameterName.isEmpty()) {
            return parameterName;
        }

        // 使用参数的实际名称，如果获取不到则使用索引
        String paramName = parameter.getName();
        if (paramName.startsWith("arg")) {
            // 如果参数名是arg0, arg1这种形式，说明没有保留参数名信息
            return "param" + paramIndex;
        }
        return paramName;
    }

    /**
     * 基于注解信息提取参数值
     * 支持集合处理，返回的集合中每个元素将作为独立的组别处理
     */
    private Object extractParameterValueWithInfo(Object arg, ParameterAnnotationInfo annotationInfo,
                                                String paramName, ParamValueProcessor.ProcessContext context) {
        DuplicateSubmitParam paramAnnotation = annotationInfo.getParamAnnotation();

        Object extractedValue = arg;

        // 1. 首先进行路径提取
        if (paramAnnotation != null && !paramAnnotation.path().isEmpty()) {
            extractedValue = parameterValueExtractor.extractValue(arg, paramAnnotation.path());
        }

        // 2. 然后使用处理器处理值
        if (paramAnnotation != null && !paramAnnotation.processor().isEmpty()) {
            String processorName = paramAnnotation.processor();
            extractedValue = processorManager.processValue(processorName, extractedValue, paramName, context);
        }

        return extractedValue;
    }

    /**
     * 尝试获取锁，支持分组功能
     * @return 锁值（获取成功）或 null（重复提交）
     */
    public String tryAcquireLock(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        // 生成分组信息
        List<ParamGroupInfo> groups = generateParamGroups(joinPoint, request, annotation);

        // 检查是否有非默认分组
        boolean hasNonDefaultGroups = groups.stream()
            .anyMatch(group -> !group.isDefaultGroup());

        // 如果没有分组或只有默认分组且分组策略是ALL_GROUPS，创建默认分组
        if (groups.isEmpty() || (!hasNonDefaultGroups && annotation.groupStrategy() == PreventDuplicateSubmit.GroupStrategy.ALL_GROUPS)) {
            // 创建一个默认分组，包含传统的key
            ParamGroupInfo defaultGroup = new ParamGroupInfo("default", 0);
            String key = generateKey(joinPoint, request, annotation);
            defaultGroup.addParam("traditional_key", key);
            groups = Collections.singletonList(defaultGroup);
        }

        // 统一使用分组方式处理
        return tryAcquireGroupLocks(groups, joinPoint, request, annotation);
    }

    /**
     * 尝试获取单个锁
     */
    private String tryAcquireSingleLock(String key, PreventDuplicateSubmit annotation) {
        long interval = annotation.timeUnit().toMillis(annotation.interval());

        try {
            // 生成唯一的value值
            String lockValue = generateLockValue();


            // 使用Lua脚本原子性地检查和设置
            Long result = redisTemplate.execute(checkAndSetScript,
                Collections.singletonList(key),
                lockValue,
                interval);

            boolean isDuplicate = result != null && result != 0;

            if (isDuplicate) {
                logger.debug("检测到重复提交: key={}, remainingTime={}ms", key, result);
                return null; // 重复提交，返回null
            } else {
                logger.debug("首次提交，已记录: key={}, lockValue={}, interval={}ms", key, lockValue, interval);
                return lockValue; // 返回锁值
            }

        } catch (Exception e) {
            logger.error("执行防重复提交检查异常: key={}", key, e);
            // 异常时为了安全起见，认为不是重复提交，返回一个特殊的锁值
            return generateLockValue();
        }
    }

    /**
     * 尝试获取分组锁
     */
    private String tryAcquireGroupLocks(List<ParamGroupInfo> groups, ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        // 过滤和排序分组
        List<ParamGroupInfo> filteredGroups = filterGroups(groups, annotation);
        List<ParamGroupInfo> sortedGroups = ParamGroupInfo.createSortedList(filteredGroups, annotation.orderByWeight());

        // 存储已获取的锁信息，用于回滚
        List<GroupLockInfo> acquiredLocks = new ArrayList<>();

        try {
            // 逐个检查每个分组
            for (ParamGroupInfo group : sortedGroups) {
                String groupKey;

                // 检查是否为默认分组（传统方式）
                if ("default".equals(group.getGroupName()) && group.getParams().containsKey("traditional_key")) {
                    // 使用传统key
                    groupKey = (String) group.getParams().get("traditional_key");
                } else {
                    // 使用分组key
                    groupKey = generateGroupKey(group, joinPoint, request, annotation);
                }

                String groupLockValue = tryAcquireSingleLock(groupKey, annotation);

                if (groupLockValue == null) {
                    // 某个分组检测到重复提交，需要释放之前获取的锁
                    logger.debug("分组检测到重复提交: group={}, actualGroup={}, 释放已获取的{}个锁",
                        group.getGroupName(), group.getActualGroupName(), acquiredLocks.size());
                    rollbackAcquiredLocks(acquiredLocks);
                    return null;
                }

                // 记录成功获取的锁，使用实际分组名称
                acquiredLocks.add(new GroupLockInfo(group.getActualGroupName(), groupKey, groupLockValue));
            }

            // 所有分组都检查通过，组合锁值
            // 使用特殊分隔符避免与Redis key中的冒号冲突
            StringBuilder lockValueBuilder = new StringBuilder();
            for (GroupLockInfo lockInfo : acquiredLocks) {
                if (lockValueBuilder.length() > 0) {
                    lockValueBuilder.append("|");
                }
                // 格式：groupName::groupKey::lockValue（使用双冒号避免冲突）
                lockValueBuilder.append(lockInfo.getGroupName())
                    .append("::")
                    .append(lockInfo.getGroupKey())
                    .append("::")
                    .append(lockInfo.getLockValue());
            }

            String combinedLockValue = lockValueBuilder.toString();
            logger.debug("所有分组检查通过: groups={}, lockValue={}", sortedGroups.size(), combinedLockValue);

            return combinedLockValue;

        } catch (Exception e) {
            logger.error("获取分组锁异常，释放已获取的{}个锁", acquiredLocks.size(), e);
            rollbackAcquiredLocks(acquiredLocks);
            return null;
        }
    }

    /**
     * 回滚已获取的锁
     */
    private void rollbackAcquiredLocks(List<GroupLockInfo> acquiredLocks) {
        for (GroupLockInfo lockInfo : acquiredLocks) {
            try {
                boolean released = releaseLockWithKey(lockInfo.getGroupKey(), lockInfo.getLockValue());
                if (released) {
                    logger.debug("回滚释放锁成功: group={}, key={}", lockInfo.getGroupName(), lockInfo.getGroupKey());
                } else {
                    logger.warn("回滚释放锁失败: group={}, key={}", lockInfo.getGroupName(), lockInfo.getGroupKey());
                }
            } catch (Exception e) {
                logger.error("回滚释放锁异常: group={}, key={}", lockInfo.getGroupName(), lockInfo.getGroupKey(), e);
            }
        }
    }

    /**
     * 分组锁信息
     */
    private static class GroupLockInfo {
        private final String groupName;
        private final String groupKey;
        private final String lockValue;

        public GroupLockInfo(String groupName, String groupKey, String lockValue) {
            this.groupName = groupName;
            this.groupKey = groupKey;
            this.lockValue = lockValue;
        }

        public String getGroupName() {
            return groupName;
        }

        public String getGroupKey() {
            return groupKey;
        }

        public String getLockValue() {
            return lockValue;
        }
    }

    /**
     * 基于已生成的key尝试获取锁
     * @param lockKey 已生成的锁key
     * @param annotation 注解配置
     * @return 锁值（获取成功）或 null（重复提交）
     */
    public String tryAcquireLockWithKey(String lockKey, PreventDuplicateSubmit annotation) {
        if (lockKey == null) {
            return null;
        }

        long interval = annotation.timeUnit().toMillis(annotation.interval());

        try {
            // 生成唯一的value值
            String lockValue = generateLockValue();

            // 确保参数类型正确
            String intervalStr = String.valueOf(interval);

            logger.debug("执行防重复检查: lockKey={}, lockValue={}, interval={}, intervalType={}",
                lockKey, lockValue, intervalStr, intervalStr.getClass().getSimpleName());

            // 验证参数
            if (lockKey == null || lockKey.isEmpty()) {
                throw new IllegalArgumentException("LockKey cannot be null or empty");
            }
            if (lockValue == null || lockValue.isEmpty()) {
                throw new IllegalArgumentException("LockValue cannot be null or empty");
            }
            if (interval <= 0) {
                throw new IllegalArgumentException("Interval must be positive, got: " + interval);
            }

            // 使用Lua脚本原子性地检查和设置
            Long result = redisTemplate.execute(checkAndSetScript,
                Collections.singletonList(lockKey),
                lockValue,
                intervalStr);

            boolean isDuplicate = result != null && result != 0;

            if (isDuplicate) {
                logger.debug("检测到重复提交: key={}, remainingTime={}ms", lockKey, result);
                return null; // 重复提交，返回null
            } else {
                logger.debug("首次提交，已记录: key={}, lockValue={}, interval={}ms", lockKey, lockValue, interval);
                return lockValue; // 返回锁值
            }

        } catch (Exception e) {
            logger.error("执行防重复提交检查异常: key={}", lockKey, e);
            // 异常时为了安全起见，认为不是重复提交，返回一个特殊的锁值
            return generateLockValue();
        }
    }

    /**
     * 基于已生成的key获取剩余时间
     * @param lockKey 已生成的锁key
     * @return 剩余时间（毫秒）
     */
    public long getRemainingTimeWithKey(String lockKey) {
        if (lockKey == null) {
            return 0;
        }

        try {
            // 使用Lua脚本获取剩余TTL
            Long ttl = redisTemplate.execute(getTtlScript, Collections.singletonList(lockKey));
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            logger.error("获取剩余时间异常: key={}", lockKey, e);
            return 0;
        }
    }

    /**
     * 释放锁，支持分组功能
     */
    public boolean releaseLock(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation, String lockValue) {
        if (lockValue == null || request == null) {
            return false;
        }

        // 统一使用分组方式处理
        return releaseGroupLocks(lockValue, joinPoint, request, annotation);
    }

    /**
     * 释放分组锁
     */
    private boolean releaseGroupLocks(String combinedLockValue, ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        // 检查是否为传统锁值（不包含冒号或分隔符）
        if (!combinedLockValue.contains(":") && !combinedLockValue.contains("|")) {
            // 传统单个锁值，直接使用传统key释放
            String key = generateKey(joinPoint, request, annotation);
            boolean released = releaseLockWithKey(key, combinedLockValue);
            logger.debug("传统锁释放{}: key={}, lockValue={}", released ? "成功" : "失败", key, combinedLockValue);
            return released;
        }

        // 分组锁值处理
        String[] groupLocks = combinedLockValue.split("\\|");
        boolean allReleased = true;

        for (String groupLock : groupLocks) {
            // 首先尝试新格式：groupName::groupKey::lockValue
            String[] parts = groupLock.split("::", 3);
            if (parts.length == 3) {
                String groupName = parts[0];
                String groupKey = parts[1];
                String groupLockValue = parts[2];

                boolean released = releaseLockWithKey(groupKey, groupLockValue);
                if (!released) {
                    allReleased = false;
                    logger.debug("分组锁释放失败: group={}, key={}", groupName, groupKey);
                } else {
                    logger.debug("分组锁释放成功: group={}, key={}", groupName, groupKey);
                }

            }
        }

        return allReleased;
    }

    /**
     * 基于已生成的key释放锁
     * @param lockKey 已生成的锁key
     * @param lockValue 锁值
     * @return 是否释放成功
     */
    public boolean releaseLockWithKey(String lockKey, String lockValue) {
        if (lockKey == null || lockValue == null) {
            logger.debug("锁key或锁值为空，跳过删除: key={}, lockValue={}", lockKey, lockValue);
            return false;
        }

        try {
            // 使用Lua脚本安全删除锁
            Long result = redisTemplate.execute(safeDeleteScript,
                Collections.singletonList(lockKey),
                lockValue);

            boolean deleted = result != null && result == 1;

            if (deleted) {
                logger.debug("成功删除锁: key={}, lockValue={}", lockKey, lockValue);
            } else {
                logger.warn("锁已过期或被其他请求删除: key={}, lockValue={}", lockKey, lockValue);
            }

            return deleted;

        } catch (Exception e) {
            logger.error("删除锁异常: key={}, lockValue={}", lockKey, lockValue, e);
            return false;
        }
    }



    /**
     * 通过方法和参数索引获取参数注解信息
     */
    private ParameterAnnotationInfo getParameterAnnotationInfo(MethodSignature signature, int paramIndex) {
        try {
            Method method = signature.getMethod();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();

            if (paramIndex < parameterAnnotations.length) {
                Annotation[] annotations = parameterAnnotations[paramIndex];

                DuplicateSubmitParam paramAnnotation = null;
                boolean hasIgnore = false;

                for (Annotation annotation : annotations) {
                    if (annotation instanceof DuplicateSubmitParam) {
                        paramAnnotation = (DuplicateSubmitParam) annotation;
                    } else if (annotation instanceof DuplicateSubmitIgnore) {
                        hasIgnore = true;
                    }
                }

                return new ParameterAnnotationInfo(paramAnnotation, hasIgnore);
            }

            return new ParameterAnnotationInfo(null, false);
        } catch (Exception e) {
            logger.debug("获取参数注解信息失败: paramIndex={}", paramIndex, e);
            return new ParameterAnnotationInfo(null, false);
        }
    }

    /**
     * 参数注解信息
     */
    private static class ParameterAnnotationInfo {
        private final DuplicateSubmitParam paramAnnotation;
        private final boolean hasIgnore;

        public ParameterAnnotationInfo(DuplicateSubmitParam paramAnnotation, boolean hasIgnore) {
            this.paramAnnotation = paramAnnotation;
            this.hasIgnore = hasIgnore;
        }

        public DuplicateSubmitParam getParamAnnotation() {
            return paramAnnotation;
        }

        public boolean hasIgnore() {
            return hasIgnore;
        }
    }

    /**
     * 生成参数分组信息
     */
    private List<ParamGroupInfo> generateParamGroups(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        Map<String, ParamGroupInfo> groupMap = new HashMap<>();

        try {
            // 获取方法参数
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Parameter[] parameters = signature.getMethod().getParameters();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            // 处理方法参数
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Object arg = args[i];
                String parameterName = parameterNames[i];

                // 跳过特殊参数
                if (isSpecialParameter(arg)) {
                    continue;
                }

                // 获取参数注解信息
                ParameterAnnotationInfo annotationInfo = getParameterAnnotationInfo(signature, i);

                // 根据策略处理参数
                if (shouldIncludeParameterWithInfo(annotationInfo, annotation)) {
                    String paramName = getParameterNameWithInfo(parameter, i, annotationInfo, parameterName);

                    // 创建处理上下文
                    String userIdentifier = extractUserIdentifier(request);
                    DefaultProcessContext context = new DefaultProcessContext(joinPoint, request, i, userIdentifier);

                    Object paramValue = extractParameterValueWithInfo(arg, annotationInfo, paramName, context);

                    // 获取分组信息
                    String groupName = getParameterGroupName(annotationInfo);
                    int groupWeight = getParameterGroupWeight(annotationInfo);

                    // 处理集合类型的参数值
                    if (processorManager.isCollection(paramValue)) {
                        // 集合中的每个元素作为独立的分组
                        java.util.List<Object> valueList = processorManager.toList(paramValue);
                        for (int j = 0; j < valueList.size(); j++) {
                            Object element = valueList.get(j);

                            // 生成基于内容的稳定分组名称
                            String actualGroupName = GroupNameGenerator.generateElementGroupName(groupName, element, j);

                            // 生成基于内容的稳定参数名称
                            String stableParamName = GroupNameGenerator.generateStableParamName(paramName, element);

                            // 获取或创建分组，使用实际分组名称作为key
                            ParamGroupInfo elementGroup = groupMap.computeIfAbsent(actualGroupName,
                                name -> new ParamGroupInfo(groupName, actualGroupName, groupWeight));

                            // 添加参数到分组，使用稳定的参数名
                            elementGroup.addParam(stableParamName, parameterValueExtractor.safeToString(element));
                        }
                    } else {
                        // 单个值的正常处理
                        ParamGroupInfo group = groupMap.computeIfAbsent(groupName,
                            name -> new ParamGroupInfo(name, groupWeight));

                        // 添加参数到分组
                        group.addParam(paramName, parameterValueExtractor.safeToString(paramValue));
                    }
                }
            }

            return new ArrayList<>(groupMap.values());

        } catch (Exception e) {
            logger.warn("生成参数分组失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取参数分组名称
     */
    private String getParameterGroupName(ParameterAnnotationInfo annotationInfo) {
        DuplicateSubmitParam paramAnnotation = annotationInfo.getParamAnnotation();
        if (paramAnnotation != null && !paramAnnotation.group().isEmpty()) {
            return paramAnnotation.group();
        }
        return ""; // 默认分组
    }

    /**
     * 获取参数分组权重
     */
    private int getParameterGroupWeight(ParameterAnnotationInfo annotationInfo) {
        DuplicateSubmitParam paramAnnotation = annotationInfo.getParamAnnotation();
        if (paramAnnotation != null) {
            return paramAnnotation.groupWeight();
        }
        return 0; // 默认权重
    }

    /**
     * 过滤分组
     */
    private List<ParamGroupInfo> filterGroups(List<ParamGroupInfo> groups, PreventDuplicateSubmit annotation) {
        PreventDuplicateSubmit.GroupStrategy strategy = annotation.groupStrategy();
        String[] specifiedGroups = annotation.groups();

        switch (strategy) {
            case ALL_GROUPS:
                return groups;

            case SPECIFIED_GROUPS:
                if (specifiedGroups.length == 0) {
                    return groups;
                }
                Set<String> includeSet = new HashSet<>(Arrays.asList(specifiedGroups));
                return groups.stream()
                    .filter(group -> includeSet.contains(group.getGroupName()))
                    .collect(Collectors.toList());

            case EXCEPT_GROUPS:
                if (specifiedGroups.length == 0) {
                    return groups;
                }
                Set<String> excludeSet = new HashSet<>(Arrays.asList(specifiedGroups));
                return groups.stream()
                    .filter(group -> !excludeSet.contains(group.getGroupName()))
                    .collect(Collectors.toList());

            default:
                return groups;
        }
    }

    /**
     * 生成分组key
     */
    private String generateGroupKey(ParamGroupInfo group, ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation) {
        StringBuilder keyBuilder = new StringBuilder();

        keyBuilder.append("duplicate_submit:");

        // 添加前缀
        if (!annotation.keyPrefix().isEmpty()) {
            keyBuilder.append(annotation.keyPrefix()).append(":");
        } else {
            // 添加方法标识
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            keyBuilder.append(signature.getDeclaringTypeName())
                    .append(".")
                    .append(signature.getName());

        }


        // 添加用户标识
        if (annotation.includeUser()) {
            String userIdentifier = extractUserIdentifier(request);
            if (userIdentifier != null) {
                keyBuilder.append(":user:").append(userIdentifier);
            }
        }

        // 添加分组标识
        keyBuilder.append(":group:").append(group.getGroupName());

        // 添加分组参数哈希
        if (!group.isEmpty()) {
            String paramsHash = DigestUtils.md5DigestAsHex(group.getParamsHash().getBytes(StandardCharsets.UTF_8));
            keyBuilder.append(":params:").append(paramsHash);
        }

        return keyBuilder.toString();
    }

    /**
     * 生成唯一的锁值
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
