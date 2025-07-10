package com.marry.ratelimit.starter.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @EnableRateLimit注解测试
 */
public class EnableRateLimitTest {

    @Test
    public void testAnnotationPresent() {
        // 创建一个使用注解的测试类
        @EnableRateLimit
        class TestClass {
        }

        // 获取注解
        EnableRateLimit annotation = TestClass.class.getAnnotation(EnableRateLimit.class);

        assertNotNull(annotation);
    }

    @Test
    public void testAnnotationPresence() {
        // 测试注解是否正确应用
        @EnableRateLimit
        class WithAnnotation {
        }

        class WithoutAnnotation {
        }

        assertTrue(WithAnnotation.class.isAnnotationPresent(EnableRateLimit.class));
        assertFalse(WithoutAnnotation.class.isAnnotationPresent(EnableRateLimit.class));
    }

    @Test
    public void testAnnotationRetention() {
        // 测试注解的保留策略
        @EnableRateLimit
        class TestClass {
        }

        Annotation[] annotations = TestClass.class.getAnnotations();
        boolean found = false;

        for (Annotation annotation : annotations) {
            if (annotation instanceof EnableRateLimit) {
                found = true;
                break;
            }
        }

        assertTrue(found, "注解应该在运行时保留");
    }
}
