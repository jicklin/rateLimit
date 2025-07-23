package io.github.jicklin.starter.ratelimit.aspect;

import io.github.jicklin.starter.ratelimit.annotation.PreventDuplicateSubmit;
import io.github.jicklin.starter.ratelimit.exception.DuplicateSubmitException;
import io.github.jicklin.starter.ratelimit.service.DuplicateSubmitService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Duplicate submit prevention aspect
 *
 * @author marry
 */
@Aspect
public class DuplicateSubmitAspect {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateSubmitAspect.class);

    private final DuplicateSubmitService duplicateSubmitService;


    public DuplicateSubmitAspect(DuplicateSubmitService duplicateSubmitService) {
        this.duplicateSubmitService = duplicateSubmitService;
        logger.debug("DuplicateSubmitAspect constructor called, Service type: {}",
            duplicateSubmitService != null ? duplicateSubmitService.getClass().getName() : "null");
    }

    @Around("@annotation(preventDuplicateSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, PreventDuplicateSubmit preventDuplicateSubmit) throws Throwable {


        String lockValue = null;

        HttpServletRequest request = null;
        Object result;
        try {

            // Get current request
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                logger.warn("Unable to get current request context, skipping duplicate submit check");
                throw new IllegalStateException("cannot get ServletRequestAttributes");
            }

            request = attributes.getRequest();

            lockValue = duplicateSubmitService.tryAcquireLock(joinPoint,request, preventDuplicateSubmit);

            if (lockValue == null) {
                // Duplicate submission detected
//                long remainingTime = duplicateSubmitService.getRemainingTimeWithKey(lockKey);

                String message = preventDuplicateSubmit.message();

                logger.debug("Duplicate submission detected: method={}, user={}, key={}",
                        joinPoint.getSignature().toShortString(),
                        extractUserInfo(request),
                        lockValue);

                throw new DuplicateSubmitException(message);
            }
            logger.debug("Duplicate submit check passed: method={}, user={}, lockValue={}",
                    joinPoint.getSignature().toShortString(),
                    extractUserInfo(request),
                    lockValue);

        } catch (DuplicateSubmitException e) {
            // Re-throw duplicate submission exception
            throw e;
        } catch (Exception e) {
            logger.error("Duplicate submit check error: method={}", joinPoint.getSignature().toShortString(), e);
        }


        try {
            // Execute original method
            result = joinPoint.proceed();

        } finally {
            // Always try to release lock regardless of success or exception
            try {
                duplicateSubmitService.releaseLock(joinPoint, request, preventDuplicateSubmit, lockValue);
            } catch (Exception e) {
                logger.error("Duplicate submit lock release error: method={}, lockValue={}", joinPoint.getSignature().toShortString(), lockValue, e);
            }
        }
        return result;

    }

    /**
     * Extract user information for logging
     */
    private String extractUserInfo(HttpServletRequest request) {
        // Try to get user information from different sources
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
     * Get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Generate lock key for duplicate submit prevention
     */
    private String generateLockKey(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit preventDuplicateSubmit) {
        return duplicateSubmitService.generateKey(joinPoint, request, preventDuplicateSubmit);
    }

    /**
     * Release duplicate submit lock (using generated key)
     */
    private void releaseLockWithKey(String lockKey, String lockValue) {
        try {
            duplicateSubmitService.releaseLockWithKey(lockKey, lockValue);
        } catch (Exception e) {
            logger.warn("Release duplicate submit lock error: key={}, lockValue={}", lockKey, lockValue, e);
        }
    }

}
