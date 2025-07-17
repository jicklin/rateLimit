package io.github.jicklin.starter.ratelimit.util;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 分组名称生成器
 * 用于生成稳定的、基于内容的分组名称
 *
 * @author marry
 */
public class GroupNameGenerator {

    private static final Pattern SAFE_CHARS = Pattern.compile("[a-zA-Z0-9_-]");
    private static final int MAX_CONTENT_LENGTH = 20;
    private static final int HASH_LENGTH = 8;

    /**
     * 生成实际分组名称
     * 基于内容生成稳定的分组名称，确保相同内容总是生成相同的分组名
     *
     * @param baseGroupName 基础分组名称
     * @param content 分组内容
     * @return 实际分组名称
     */
    public static String generateActualGroupName(String baseGroupName, Object content) {
        if (content == null) {
            return baseGroupName + "_null";
        }

        String contentStr = content.toString();

        // 如果内容为空，使用默认名称
        if (contentStr.isEmpty()) {
            return baseGroupName + "_empty";
        }

        // 生成安全的内容标识
        String safeContent = generateSafeContent(contentStr);

        // 生成内容哈希（用于确保唯一性）
        String contentHash = generateContentHash(contentStr);

        // 组合生成实际分组名称
        return baseGroupName + "_" + safeContent + "_" + contentHash;
    }

    /**
     * 生成安全的内容标识
     * 提取内容中的安全字符，限制长度
     */
    public static String generateSafeContent(String content) {
        StringBuilder safeContent = new StringBuilder();

        // 提取安全字符
        for (char c : content.toCharArray()) {
            if (SAFE_CHARS.matcher(String.valueOf(c)).matches()) {
                safeContent.append(c);
                if (safeContent.length() >= MAX_CONTENT_LENGTH) {
                    break;
                }
            }
        }

        // 如果没有安全字符，使用长度标识
        if (safeContent.length() == 0) {
            safeContent.append("len").append(content.length());
        }

        return safeContent.toString();
    }

    /**
     * 生成内容哈希
     * 使用MD5哈希的前8位确保唯一性
     */
    private static String generateContentHash(String content) {
        String md5Hash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        return md5Hash.substring(0, Math.min(HASH_LENGTH, md5Hash.length()));
    }

    /**
     * 生成简化的分组名称
     * 对于简单内容，生成更简洁的分组名称
     *
     * @param baseGroupName 基础分组名称
     * @param content 分组内容
     * @return 简化的分组名称
     */
    public static String generateSimpleGroupName(String baseGroupName, Object content) {
        if (content == null) {
            return baseGroupName + "_null";
        }

        String contentStr = content.toString();

        // 如果内容很短且只包含安全字符，直接使用
        if (contentStr.length() <= 10 && isAllSafeChars(contentStr)) {
            return baseGroupName + "_" + contentStr;
        }

        // 否则使用完整的生成逻辑
        return generateActualGroupName(baseGroupName, content);
    }

    /**
     * 检查字符串是否只包含安全字符
     */
    private static boolean isAllSafeChars(String str) {
        for (char c : str.toCharArray()) {
            if (!SAFE_CHARS.matcher(String.valueOf(c)).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 生成集合元素的分组名称
     * 为集合中的每个元素生成稳定的分组名称
     *
     * @param baseGroupName 基础分组名称
     * @param element 集合元素
     * @param elementIndex 元素索引（用于调试）
     * @return 元素分组名称
     */
    public static String generateElementGroupName(String baseGroupName, Object element, int elementIndex) {
        if (element == null) {
            return baseGroupName + "_null_" + elementIndex;
        }

        String elementStr = element.toString();

        // 对于空字符串，使用索引
        if (elementStr.isEmpty()) {
            return baseGroupName + "_empty_" + elementIndex;
        }

        // 生成基于内容的稳定名称
        String safeContent = generateSafeContent(elementStr);
        String contentHash = generateContentHash(elementStr);

        return baseGroupName + "_" + safeContent + "_" + contentHash;
    }

    /**
     * 批量生成分组名称
     * 确保同一批次中的分组名称不重复
     *
     * @param baseGroupName 基础分组名称
     * @param elements 元素列表
     * @return 分组名称数组
     */
    public static String[] generateBatchGroupNames(String baseGroupName, Object[] elements) {
        String[] groupNames = new String[elements.length];

        for (int i = 0; i < elements.length; i++) {
            groupNames[i] = generateElementGroupName(baseGroupName, elements[i], i);
        }

        return groupNames;
    }

    /**
     * 生成稳定的参数名称
     * 基于内容生成稳定的参数名称，确保相同内容总是生成相同的参数名
     *
     * @param baseParamName 基础参数名称
     * @param element 元素内容
     * @return 稳定的参数名称
     */
    public static String generateStableParamName(String baseParamName, Object element) {
        if (element == null) {
            return baseParamName + "[null]";
        }

        String elementStr = element.toString();

        // 如果内容为空，使用特殊标识
        if (elementStr.isEmpty()) {
            return baseParamName + "[empty]";
        }

        // 生成安全的内容标识
        String safeContent = generateSafeContent(elementStr);

        return baseParamName + "[" + safeContent + "]";
    }

    /**
     * 批量生成稳定的参数名称
     *
     * @param baseParamName 基础参数名称
     * @param elements 元素列表
     * @return 稳定的参数名称数组
     */
    public static String[] generateStableParamNames(String baseParamName, Object[] elements) {
        String[] paramNames = new String[elements.length];

        for (int i = 0; i < elements.length; i++) {
            paramNames[i] = generateStableParamName(baseParamName, elements[i]);
        }

        return paramNames;
    }

    /**
     * 验证分组名称的有效性
     *
     * @param groupName 分组名称
     * @return 是否有效
     */
    public static boolean isValidGroupName(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return false;
        }

        // 检查长度限制
        if (groupName.length() > 100) {
            return false;
        }

        // 检查字符有效性
        for (char c : groupName.toCharArray()) {
            if (!SAFE_CHARS.matcher(String.valueOf(c)).matches() && c != '_') {
                return false;
            }
        }

        return true;
    }
}
