package com.vector.utils.pdf;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 字符串转义工具类
 * 用于处理表格内容中的特殊字符，防止注入攻击
 *
 * @author YuanJie
 * @date 2025/3/10
 */
@Slf4j
public class StringEscapeUtil {

    /**
     * 白名单正则表达式（只允许常用字符）
     */
    private static final Pattern SAFE_CHARS_PATTERN =
            // 修改后支持识别★和⚫的正则表达式
            Pattern.compile("[^" +                     // 否定字符类开始
                    "\\p{L}" +                 // Unicode字母类别
                    "\\p{N}" +                 // Unicode数字类别
                    "\\p{P}" +                 // Unicode标点类别
                    "\\p{Z}" +                 // Unicode分隔符类别
                    "\\p{M}" +                 // Unicode标记类别
                    "\\p{Sc}\\p{Sk}\\p{Sm}" + // 货币/修饰/数学符号
                    "\\u2605" +                // ★ 符号（Unicode码位：U+2605）
                    "\\u26AB" +                // ⚫ 符号（Unicode码位：U+26AA）
                    "\\u2611" +                // checkbox 符号（Unicode码位：U+2611）
                    "\\uf052" +                //  符号（Unicode码位：U+uf052）
                    "]+"                       // 否定字符类结束
            );
    /**
     * SQL注入风险关键字
     */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(?i)(\\b(select|insert|update|delete|drop|alter|create|truncate|declare|exec|union|where)\\b)");

    /**
     * 执行输入过滤（白名单过滤）
     *
     * @param input 输入字符串
     * @return 过滤后的安全字符串
     */
    public static String filterInput(String input) {
        if (input == null) {
            return "";
        }
        // 使用白名单过滤非常用字符
        return SAFE_CHARS_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * 执行HTML实体编码
     *
     * @param input 输入字符串
     * @return HTML实体编码后的字符串
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '&' -> escaped.append("&amp;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#x27;");
                case '/' -> escaped.append("&#x2F;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * 执行SQL转义
     *
     * @param input 输入字符串
     * @return SQL转义后的字符串
     */
    public static String escapeSql(String input) {
        if (input == null) {
            return "";
        }
        // 检测SQL注入风险
        if (SQL_INJECTION_PATTERN.matcher(input).find()) {
            log.warn("检测到潜在SQL注入风险: {}", input);
        }
        // 替换单引号和反斜杠
        return input.replace("'", "''").replace("\\", "\\\\");
    }

    /**
     * 执行JSON转义
     *
     * @param input 输入字符串
     * @return JSON转义后的字符串
     */
    public static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '/' -> escaped.append("\\/");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * 根据上下文类型执行适当的转义
     *
     * @param input 输入字符串
     * @param contextType 上下文类型（HTML/SQL/JSON）
     * @return 转义后的字符串
     */
    public static String escapeByContext(String input, ContextType contextType) {
        // 首先执行输入过滤
        String filtered = filterInput(input);

        // 根据上下文类型执行特定转义
        return switch (contextType) {
            case HTML -> escapeHtml(filtered);
            case SQL -> escapeSql(filtered);
            case JSON -> escapeJson(filtered);
            default -> filtered;
        };
    }

    /**
     * 上下文类型枚举
     */
    public enum ContextType {
        HTML, SQL, JSON, PLAIN
    }
}
