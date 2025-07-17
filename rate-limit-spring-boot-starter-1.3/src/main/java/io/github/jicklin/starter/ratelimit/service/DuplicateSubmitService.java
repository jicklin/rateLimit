package io.github.jicklin.starter.ratelimit.service;

import io.github.jicklin.starter.ratelimit.annotation.PreventDuplicateSubmit;
import org.aspectj.lang.ProceedingJoinPoint;

import javax.servlet.http.HttpServletRequest;

/**
 * 防重复提交服务接口
 *
 * @author marry
 */
public interface DuplicateSubmitService {

    /**
     * 记录请求，防止重复提交
     *
     * @param joinPoint  切点
     * @param request    HTTP请求
     * @param annotation 注解信息
     */
    void recordSubmit(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation);

    /**
     * 生成防重复提交的key
     *
     * @param joinPoint  切点
     * @param request    HTTP请求
     * @param annotation 注解信息
     * @return Redis key
     */
    String generateKey(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation);



    public String tryAcquireLockWithKey(String lockKey, PreventDuplicateSubmit annotation);

    long getRemainingTimeWithKey(String key);

    public boolean releaseLockWithKey(String lockKey, String lockValue);


    public String tryAcquireLock(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation);


    public boolean releaseLock(ProceedingJoinPoint joinPoint, HttpServletRequest request, PreventDuplicateSubmit annotation, String lockValue);






}



