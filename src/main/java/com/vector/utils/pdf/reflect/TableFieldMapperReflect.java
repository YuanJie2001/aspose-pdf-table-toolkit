package com.vector.utils.pdf.reflect;

import com.vector.utils.pdf.annotation.TableFieldMap;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表格字段映射工具类
 * 用于处理TableFieldMap注解的映射逻辑
 *
 * @author YuanJie
 * @date 2025/3/5
 */
@Slf4j
public class TableFieldMapperReflect {

    // 缓存各个类的字段映射关系，避免重复反射
    private static final Map<Class<?>, Map<String, Field>> CLASS_FIELD_CACHE = new ConcurrentHashMap<>();

    // 默认日期格式化器
    private static final SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy");

    /**
     * 加载指定类中的字段映射关系
     *
     * @param clazz 需要加载映射关系的类
     * @return 映射关系Map
     */
    public static Map<String, Field> loadFieldMappings(Class<?> clazz) {
        return CLASS_FIELD_CACHE.computeIfAbsent(clazz, cls -> {
            Map<String, Field> fieldMap = new HashMap<>();
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                TableFieldMap annotation = field.getAnnotation(TableFieldMap.class);
                if (annotation != null) {
                    String tableFieldName = annotation.value();
                    fieldMap.put(tableFieldName, field);
                    log.debug("加载字段映射: {} -> {}", tableFieldName, field.getName());
                }
            }
            log.info("成功加载 {} 个字段映射", fieldMap.size());
            return fieldMap;
        });
    }

    /**
     * 使用注解方式将字段值映射到目标对象的对应属性
     *
     * @param target 目标对象
     * @param key 表格中的字段名
     * @param value 字段值
     * @param fieldCache 字段映射缓存
     */
    public static void mapFieldByAnnotation(Object target, String key, String value,
                                            Map<String, Field> fieldCache) {
        mapFieldByAnnotation(target, key, value, fieldCache, DEFAULT_DATE_FORMAT);
    }

    /**
     * 使用注解方式将字段值映射到目标对象的对应属性
     *
     * @param target 目标对象
     * @param key 表格中的字段名
     * @param value 字段值
     * @param fieldCache 字段映射缓存
     * @param dateFormat 日期格式化器
     */
    public static void mapFieldByAnnotation(Object target, String key, String value,
                                            Map<String, Field> fieldCache, SimpleDateFormat dateFormat) {
        Field field = fieldCache.get(key);
        if (field == null) {
            log.warn("未找到映射字段: {}", key);
            return;
        }

        try {
            Class<?> fieldType = field.getType();
            Object convertedValue = null;

            if (String.class.equals(fieldType)) {
                convertedValue = value;
            } else if (Date.class.equals(fieldType)) {
                try {
                    convertedValue = dateFormat.parse(value);
                } catch (ParseException e) {
                    log.error("日期解析失败: {} (格式要求: {})", value, dateFormat.toPattern(), e);
                }
            } else if (Number.class.isAssignableFrom(fieldType) || fieldType.isPrimitive()) {
                convertedValue = parseNumber(fieldType, value);
            } else if (Boolean.class.equals(fieldType) || boolean.class.equals(fieldType)) {
                convertedValue = parseBoolean(value);
            } else {
                log.warn("不支持的字段类型: {}", fieldType.getName());
            }

            if (convertedValue != null) {
                field.set(target, convertedValue);
            }
        } catch (Exception e) {
            log.error("设置字段值失败: {} = {}, 原因: {}", field.getName(), value, e.getMessage());
        }
    }

    private static Object parseNumber(Class<?> type, String value) {
        try {
            if (Long.class.equals(type) || long.class.equals(type)) {
                return Long.parseLong(value);
            } else if (Integer.class.equals(type) || int.class.equals(type)) {
                return Integer.parseInt(value);
            } else if (Double.class.equals(type) || double.class.equals(type)) {
                return Double.parseDouble(value);
            }
        } catch (NumberFormatException e) {
            log.error("数字解析失败: {} (目标类型: {})", value, type.getSimpleName(), e);
        }
        return null;
    }

    private static boolean parseBoolean(String value) {
        boolean result = Boolean.parseBoolean(value);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            log.warn("非标准布尔值转换: {} -> {}", value, result);
        }
        return result;
    }

}
