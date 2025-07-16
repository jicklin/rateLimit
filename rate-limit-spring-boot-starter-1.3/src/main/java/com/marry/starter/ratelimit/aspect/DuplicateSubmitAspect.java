package com.marry.starter.ratelimit.aspect;

import com.marry.starter.ratelimit.annotation.PreventDuplicateSubmit;
import com.marry.starter.ratelimit.exception.DuplicateSubmitException;
import com.marry.starter.ratelimit.service.DuplicateSubmitService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 防重复提交切面
 *
 * @author marry
 */
@Aspect
@Component
@Order(1) // 设置较高优先级，在其他切面之前执行
public class DuplicateSubmitAspect {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateSubmitAspect.class);

    @Autowired
    private DuplicateSubmitService duplicateSubmitService;

    @Around("@annotation(preventDuplicateSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, PreventDuplicateSubmit preventDuplicateSubmit) throws Throwable {
        // 获取当前请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            logger.warn("无法获取当前请求上下文，跳过防重复提交检查");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();

        String lockValue = null;
        String lockKey = null;

        try {
            // 生成锁的key（只生成一次）
            lockKey = generateLockKey(joinPoint, request, preventDuplicateSubmit);

            lockValue = duplicateSubmitService.tryAcquireLock(joinPoint,request, preventDuplicateSubmit);

            if (lockValue == null) {
                // 重复提交
//                long remainingTime = duplicateSubmitService.getRemainingTimeWithKey(lockKey);

                String message = preventDuplicateSubmit.message();

                logger.info("检测到重复提交: method={}, user={}, key={}",
                        joinPoint.getSignature().toShortString(),
                        extractUserInfo(request),
                        lockKey);

                throw new DuplicateSubmitException(message);
            }


            // 执行原方法
            Object result = joinPoint.proceed();

            logger.debug("防重复提交检查通过: method={}, user={}, key={}, lockValue={}",
                joinPoint.getSignature().toShortString(),
                extractUserInfo(request),
                lockKey,
                lockValue);

            return result;

        } catch (DuplicateSubmitException e) {
            // 重新抛出重复提交异常
            throw e;
        } catch (Exception e) {
            logger.error("防重复提交检查异常: method={}", joinPoint.getSignature().toShortString(), e);
            // 发生异常时不阻止方法执行，但记录错误日志
            return joinPoint.proceed();
        } finally {
            // 无论成功还是异常，都要尝试释放锁
            if (lockValue != null && lockKey != null) {
                releaseLockWithKey(lockKey, lockValue);
            }
        }
    }

    /**
     * 提取用户信息用于日志记录
     */
    private String extractUserInfo(HttpServletRequest request) {
        // 尝试从不同地方获取用户信息
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            return "token:" + authorization.substring(0, Math.min(authorization.length(), 20)) + "...";
        }

        String userId = request.getParameter("netUserId");
        if (userId != null && !userId.isEmpty()) {
            return "userId:" + userId;
        }

        if (request.getSession(false) != null) {
            return "session:" + request.getSession().getId();
        }

        return "ip:" + getClientIpAddress(request);
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 生成防重复提交的锁key
     */
    private String generateLockKey(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit preventDuplicateSubmit) {
        return duplicateSubmitService.generateKey(joinPoint, request, preventDuplicateSubmit);
    }

    /**
     * 释放防重复提交锁（使用已生成的key）
     */
    private void releaseLockWithKey(String lockKey, String lockValue) {
        try {
            duplicateSubmitService.releaseLockWithKey(lockKey, lockValue);
        } catch (Exception e) {
            logger.warn("释放防重复提交锁异常: key={}, lockValue={}", lockKey, lockValue, e);
        }
    }

}
