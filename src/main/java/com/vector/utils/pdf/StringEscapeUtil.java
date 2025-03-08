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
            Pattern.compile("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\p{M}\\p{Sc}\\p{Sk}\\p{Sm}]+");

    /**
     * SQL注入风险关键字
     */
    private static final Pattern SQL_INJECTION_PATTERN = 
            Pattern.compile("(?i)(\\b(select|insert|update|delete|drop|alter|create|truncate|declare|exec|union|where)\\b)");

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
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '&':
                    escaped.append("&amp;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#x27;");
                    break;
                case '/':
                    escaped.append("&#x2F;");
                    break;
                default:
                    escaped.append(c);
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
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '/':
                    escaped.append("\\/");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(c);
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
        switch (contextType) {
            case HTML:
                return escapeHtml(filtered);
            case SQL:
                return escapeSql(filtered);
            case JSON:
                return escapeJson(filtered);
            default:
                return filtered;
        }
    }

    /**
     * 上下文类型枚举
     */
    public enum ContextType {
        HTML,
        SQL,
        JSON,
        PLAIN
    }
}
