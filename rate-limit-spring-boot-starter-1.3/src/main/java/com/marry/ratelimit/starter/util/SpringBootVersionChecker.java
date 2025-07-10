package com.marry.ratelimit.starter.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootVersion;

/**
 * SpringBoot版本检查工具
 */
public class SpringBootVersionChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringBootVersionChecker.class);
    
    /**
     * 检查SpringBoot版本兼容性
     *
     * @return 是否兼容
     */
    public static boolean isCompatible() {
        try {
            String version = SpringBootVersion.getVersion();
            if (version == null) {
                logger.warn("无法获取SpringBoot版本信息");
                return true; // 无法确定版本时默认兼容
            }

            logger.info("当前SpringBoot版本: {}", version);

            // 解析版本号
            String[] parts = version.split("\\.");
            if (parts.length < 2) {
                logger.warn("SpringBoot版本格式异常: {}", version);
                return true;
            }

            int majorVersion = Integer.parseInt(parts[0]);
            int minorVersion = Integer.parseInt(parts[1]);

            // 支持SpringBoot 1.3+（有限支持）
            if (majorVersion == 1 && minorVersion >= 3) {
                logger.warn("检测到SpringBoot 1.x版本: {}，功能可能受限，建议升级到2.0+版本", version);
                return true; // 有限支持
            }

            // 支持SpringBoot 2.0+（完全支持）
            if (majorVersion >= 2) {
                logger.info("SpringBoot版本完全兼容: {}", version);
                return true;
            }

            logger.error("SpringBoot版本过低，不支持当前版本: {}，最低要求1.3.0+", version);
            return false;

        } catch (Exception e) {
            logger.error("检查SpringBoot版本时发生异常", e);
            return true; // 异常时默认兼容
        }
    }
    
    /**
     * 获取当前SpringBoot版本
     * 
     * @return 版本字符串
     */
    public static String getCurrentVersion() {
        try {
            return SpringBootVersion.getVersion();
        } catch (Exception e) {
            logger.error("获取SpringBoot版本失败", e);
            return "unknown";
        }
    }
    
    /**
     * 检查是否为SpringBoot 2.x版本
     * 
     * @return 是否为2.x版本
     */
    public static boolean isSpringBoot2x() {
        try {
            String version = SpringBootVersion.getVersion();
            if (version == null) {
                return false;
            }
            return version.startsWith("2.");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查是否为SpringBoot 3.x版本
     *
     * @return 是否为3.x版本
     */
    public static boolean isSpringBoot3x() {
        try {
            String version = SpringBootVersion.getVersion();
            if (version == null) {
                return false;
            }
            return version.startsWith("3.");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否为SpringBoot 1.x版本
     *
     * @return 是否为1.x版本
     */
    public static boolean isSpringBoot1x() {
        try {
            String version = SpringBootVersion.getVersion();
            if (version == null) {
                return false;
            }
            return version.startsWith("1.");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取版本兼容性级别
     *
     * @return 兼容性级别
     */
    public static CompatibilityLevel getCompatibilityLevel() {
        try {
            String version = SpringBootVersion.getVersion();
            if (version == null) {
                return CompatibilityLevel.UNKNOWN;
            }

            String[] parts = version.split("\\.");
            if (parts.length < 2) {
                return CompatibilityLevel.UNKNOWN;
            }

            int majorVersion = Integer.parseInt(parts[0]);
            int minorVersion = Integer.parseInt(parts[1]);

            if (majorVersion == 1) {
                if (minorVersion >= 3) {
                    return CompatibilityLevel.LIMITED; // 1.3+ 有限支持
                } else {
                    return CompatibilityLevel.UNSUPPORTED; // 1.3以下不支持
                }
            } else if (majorVersion == 2) {
                return CompatibilityLevel.FULL; // 2.x 完全支持
            } else if (majorVersion >= 3) {
                return CompatibilityLevel.PARTIAL; // 3.x+ 部分支持
            }

            return CompatibilityLevel.UNKNOWN;

        } catch (Exception e) {
            logger.error("获取版本兼容性级别失败", e);
            return CompatibilityLevel.UNKNOWN;
        }
    }

    /**
     * 兼容性级别枚举
     */
    public enum CompatibilityLevel {
        FULL("完全兼容"),
        PARTIAL("部分兼容"),
        LIMITED("有限支持"),
        UNSUPPORTED("不支持"),
        UNKNOWN("未知");

        private final String description;

        CompatibilityLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
