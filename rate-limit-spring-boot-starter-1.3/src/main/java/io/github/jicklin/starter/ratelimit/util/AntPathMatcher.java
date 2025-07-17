package io.github.jicklin.starter.ratelimit.util;

/**
 * Ant风格路径匹配工具类
 * 支持 ? * ** 通配符
 */
public class AntPathMatcher {

    private static final String DEFAULT_PATH_SEPARATOR = "/";

    /**
     * 检查路径是否匹配模式
     *
     * @param pattern 模式字符串
     * @param path 路径字符串
     * @return 是否匹配
     */
    public static boolean match(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }

        return doMatch(pattern, path, true);
    }

    /**
     * 执行匹配逻辑
     *
     * @param pattern 模式
     * @param path 路径
     * @param fullMatch 是否完全匹配
     * @return 是否匹配
     */
    private static boolean doMatch(String pattern, String path, boolean fullMatch) {
        if (path.startsWith(DEFAULT_PATH_SEPARATOR) != pattern.startsWith(DEFAULT_PATH_SEPARATOR)) {
            return false;
        }

        String[] pattDirs = tokenizePattern(pattern);
        String[] pathDirs = tokenizePath(path);

        int pattIdxStart = 0;
        int pattIdxEnd = pattDirs.length - 1;
        int pathIdxStart = 0;
        int pathIdxEnd = pathDirs.length - 1;

        // 匹配开始部分
        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            String pattDir = pattDirs[pattIdxStart];
            if ("**".equals(pattDir)) {
                break;
            }
            if (!matchStrings(pattDir, pathDirs[pathIdxStart])) {
                return false;
            }
            pattIdxStart++;
            pathIdxStart++;
        }

        if (pathIdxStart > pathIdxEnd) {
            // 路径已经匹配完
            if (pattIdxStart > pattIdxEnd) {
                return pattern.endsWith(DEFAULT_PATH_SEPARATOR) == path.endsWith(DEFAULT_PATH_SEPARATOR);
            }
            if (!fullMatch) {
                return true;
            }
            if (pattIdxStart == pattIdxEnd && "*".equals(pattDirs[pattIdxStart]) && path.endsWith(DEFAULT_PATH_SEPARATOR)) {
                return true;
            }
            for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (!"**".equals(pattDirs[i])) {
                    return false;
                }
            }
            return true;
        } else if (pattIdxStart > pattIdxEnd) {
            // 模式已经匹配完，但路径还有剩余
            return false;
        } else if (!fullMatch && "**".equals(pattDirs[pattIdxStart])) {
            // 部分匹配且遇到**
            return true;
        }

        // 匹配结束部分
        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            String pattDir = pattDirs[pattIdxEnd];
            if ("**".equals(pattDir)) {
                break;
            }
            if (!matchStrings(pattDir, pathDirs[pathIdxEnd])) {
                return false;
            }
            pattIdxEnd--;
            pathIdxEnd--;
        }

        if (pathIdxStart > pathIdxEnd) {
            // 路径已经匹配完
            for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (!"**".equals(pattDirs[i])) {
                    return false;
                }
            }
            return true;
        }

        // 处理中间的**
        while (pattIdxStart != pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            int patIdxTmp = -1;
            for (int i = pattIdxStart + 1; i <= pattIdxEnd; i++) {
                if ("**".equals(pattDirs[i])) {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == pattIdxStart + 1) {
                // '**/**' 情况
                pattIdxStart++;
                continue;
            }
            // 找到下一个**之前的模式
            int patLength = (patIdxTmp - pattIdxStart - 1);
            int strLength = (pathIdxEnd - pathIdxStart + 1);
            int foundIdx = -1;

            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    String subPat = pattDirs[pattIdxStart + j + 1];
                    String subStr = pathDirs[pathIdxStart + i + j];
                    if (!matchStrings(subPat, subStr)) {
                        continue strLoop;
                    }
                }
                foundIdx = pathIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            pattIdxStart = patIdxTmp;
            pathIdxStart = foundIdx + patLength;
        }

        for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
            if (!"**".equals(pattDirs[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * 分割模式字符串
     */
    private static String[] tokenizePattern(String pattern) {
        return tokenize(pattern);
    }

    /**
     * 分割路径字符串
     */
    private static String[] tokenizePath(String path) {
        return tokenize(path);
    }

    /**
     * 分割字符串
     */
    private static String[] tokenize(String str) {
        return str.split(DEFAULT_PATH_SEPARATOR);
    }

    /**
     * 匹配字符串（支持?和*通配符）
     */
    private static boolean matchStrings(String pattern, String str) {
        if (pattern == null || str == null) {
            return false;
        }

        char[] patArr = pattern.toCharArray();
        char[] strArr = str.toCharArray();
        int patIdxStart = 0;
        int patIdxEnd = patArr.length - 1;
        int strIdxStart = 0;
        int strIdxEnd = strArr.length - 1;
        char ch;

        boolean containsStar = false;
        for (char c : patArr) {
            if (c == '*') {
                containsStar = true;
                break;
            }
        }

        if (!containsStar) {
            // 没有*，只需要检查长度和?匹配
            if (patIdxEnd != strIdxEnd) {
                return false;
            }
            for (int i = 0; i <= patIdxEnd; i++) {
                ch = patArr[i];
                if (ch != '?' && ch != strArr[i]) {
                    return false;
                }
            }
            return true;
        }

        if (patIdxEnd == 0) {
            return true; // 只有一个*
        }

        // 匹配开始部分
        while ((ch = patArr[patIdxStart]) != '*' && strIdxStart <= strIdxEnd) {
            if (ch != '?' && ch != strArr[strIdxStart]) {
                return false;
            }
            patIdxStart++;
            strIdxStart++;
        }

        if (strIdxStart > strIdxEnd) {
            // 字符串已经匹配完
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (patArr[i] != '*') {
                    return false;
                }
            }
            return true;
        }

        // 匹配结束部分
        while ((ch = patArr[patIdxEnd]) != '*' && strIdxStart <= strIdxEnd) {
            if (ch != '?' && ch != strArr[strIdxEnd]) {
                return false;
            }
            patIdxEnd--;
            strIdxEnd--;
        }

        if (strIdxStart > strIdxEnd) {
            // 字符串已经匹配完
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (patArr[i] != '*') {
                    return false;
                }
            }
            return true;
        }

        // 处理中间的*
        while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
            int patIdxTmp = -1;
            for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
                if (patArr[i] == '*') {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patIdxStart + 1) {
                // 连续的*
                patIdxStart++;
                continue;
            }

            // 找到下一个*之前的模式
            int patLength = (patIdxTmp - patIdxStart - 1);
            int strLength = (strIdxEnd - strIdxStart + 1);
            int foundIdx = -1;

            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    ch = patArr[patIdxStart + j + 1];
                    if (ch != '?' && ch != strArr[strIdxStart + i + j]) {
                        continue strLoop;
                    }
                }
                foundIdx = strIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            patIdxStart = patIdxTmp;
            strIdxStart = foundIdx + patLength;
        }

        for (int i = patIdxStart; i <= patIdxEnd; i++) {
            if (patArr[i] != '*') {
                return false;
            }
        }

        return true;
    }
}
