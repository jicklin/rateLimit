package com.marry.ratelimit.util;

import java.text.DecimalFormat;

/**
 * 数学工具类 - 用于Freemarker模板中的安全数值计算
 */
public class MathUtils {
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##");
    
    /**
     * 安全的除法运算，避免除零错误
     * 
     * @param dividend 被除数
     * @param divisor 除数
     * @return 除法结果，如果除数为0则返回0
     */
    public double safeDivide(Number dividend, Number divisor) {
        if (dividend == null || divisor == null) {
            return 0.0;
        }
        
        double divisorValue = divisor.doubleValue();
        if (divisorValue == 0.0) {
            return 0.0;
        }
        
        return dividend.doubleValue() / divisorValue;
    }
    
    /**
     * 计算百分比，避免除零错误
     * 
     * @param part 部分值
     * @param total 总值
     * @return 百分比，如果总值为0则返回0
     */
    public double percentage(Number part, Number total) {
        if (part == null || total == null) {
            return 0.0;
        }
        
        double totalValue = total.doubleValue();
        if (totalValue == 0.0) {
            return 0.0;
        }
        
        return (part.doubleValue() / totalValue) * 100.0;
    }
    
    /**
     * 格式化数字为字符串，保留指定小数位
     * 
     * @param number 数字
     * @param decimals 小数位数
     * @return 格式化后的字符串
     */
    public String format(Number number, int decimals) {
        if (number == null) {
            return "0";
        }
        
        DecimalFormat formatter = new DecimalFormat();
        formatter.setMaximumFractionDigits(decimals);
        formatter.setMinimumFractionDigits(0);
        
        return formatter.format(number.doubleValue());
    }
    
    /**
     * 格式化数字为字符串，保留2位小数
     * 
     * @param number 数字
     * @return 格式化后的字符串
     */
    public String format(Number number) {
        return format(number, 2);
    }
    
    /**
     * 安全的数值获取，如果为null则返回默认值
     * 
     * @param value 数值
     * @param defaultValue 默认值
     * @return 安全的数值
     */
    public double safe(Number value, double defaultValue) {
        return value != null ? value.doubleValue() : defaultValue;
    }
    
    /**
     * 安全的数值获取，如果为null则返回0
     * 
     * @param value 数值
     * @return 安全的数值
     */
    public double safe(Number value) {
        return safe(value, 0.0);
    }
    
    /**
     * 检查数值是否有效（非null且非NaN）
     * 
     * @param value 数值
     * @return 是否有效
     */
    public boolean isValid(Number value) {
        if (value == null) {
            return false;
        }
        
        double doubleValue = value.doubleValue();
        return !Double.isNaN(doubleValue) && !Double.isInfinite(doubleValue);
    }
    
    /**
     * 限制数值在指定范围内
     * 
     * @param value 数值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的数值
     */
    public double clamp(Number value, double min, double max) {
        if (value == null) {
            return min;
        }
        
        double doubleValue = value.doubleValue();
        return Math.max(min, Math.min(max, doubleValue));
    }
}
