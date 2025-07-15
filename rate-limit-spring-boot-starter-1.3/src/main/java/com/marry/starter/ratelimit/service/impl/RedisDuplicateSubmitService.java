package com.marry.starter.ratelimit.service.impl;

import com.marry.starter.ratelimit.annotation.DuplicateSubmitIgnore;
import com.marry.starter.ratelimit.annotation.DuplicateSubmitParam;
import com.marry.starter.ratelimit.annotation.PreventDuplicateSubmit;
import com.marry.starter.ratelimit.service.DuplicateSubmitService;
import com.marry.starter.ratelimit.strategy.UserIdentifierExtractor;
import com.marry.starter.ratelimit.util.ParameterValueExtractor;
import com.marry.starter.ratelimit.util.RedisKeyGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 基于Redis的防重复提交服务实现
 *
 * @author marry
 */
@Service
public class RedisDuplicateSubmitService implements DuplicateSubmitService {

    private static final Logger logger = LoggerFactory.getLogger(RedisDuplicateSubmitService.class);



    /**
     * Lua脚本：原子性地检查和设置防重复提交key
     * 返回值：
     * - 0: 设置成功，不是重复提交
     * - 剩余TTL（毫秒）: 如果是重复提交，返回剩余时间
     */
    private static final String LUA_SCRIPT_CHECK_AND_SET =
        "local key = KEYS[1]\n" +
        "local value = ARGV[1]\n" +
        "local ttl = tonumber(ARGV[2])\n" +
        "local exists = redis.call('EXISTS', key)\n" +
        "if exists == 0 then\n" +
        "    redis.call('SET', key, value, 'PX', ttl)\n" +
        "    return 0\n" +
        "else\n" +
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
            processMethodParameters(params, parameters, args, annotation, signature);

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
    private void processMethodParameters(Map<String, Object> params, Parameter[] parameters, Object[] args, PreventDuplicateSubmit annotation, MethodSignature signature) {
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];

            // 跳过特殊参数
            if (isSpecialParameter(arg)) {
                continue;
            }

            // 获取参数注解信息
            ParameterAnnotationInfo annotationInfo = getParameterAnnotationInfo(signature, i);

            // 根据策略处理参数
            if (shouldIncludeParameterWithInfo(annotationInfo, annotation)) {
                String paramName = getParameterNameWithInfo(parameter, i, annotationInfo);
                Object paramValue = extractParameterValueWithInfo(arg, annotationInfo);
                params.put(paramName, parameterValueExtractor.safeToString(paramValue));
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
               arg instanceof javax.servlet.http.HttpSession;
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
    private String getParameterNameWithInfo(Parameter parameter, int paramIndex, ParameterAnnotationInfo annotationInfo) {
        // 优先使用@DuplicateSubmitParam中的alias
        DuplicateSubmitParam paramAnnotation = annotationInfo.getParamAnnotation();
        if (paramAnnotation != null && !paramAnnotation.alias().isEmpty()) {
            return paramAnnotation.alias();
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
     */
    private Object extractParameterValueWithInfo(Object arg, ParameterAnnotationInfo annotationInfo) {
        DuplicateSubmitParam paramAnnotation = annotationInfo.getParamAnnotation();

        if (paramAnnotation != null && !paramAnnotation.path().isEmpty()) {
            // 使用路径提取值
            return parameterValueExtractor.extractValue(arg, paramAnnotation.path());
        }

        // 返回整个参数对象
        return arg;
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

            // 使用Lua脚本原子性地检查和设置
            Long result = redisTemplate.execute(checkAndSetScript,
                Collections.singletonList(lockKey),
                lockValue,
                String.valueOf(interval));

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
                logger.debug("锁已过期或被其他请求删除: key={}, lockValue={}", lockKey, lockValue);
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
     * 生成唯一的锁值
     */
    private String generateLockValue() {
        return Thread.currentThread().getId() + ":" + System.currentTimeMillis() + ":" + System.nanoTime();
    }
}
