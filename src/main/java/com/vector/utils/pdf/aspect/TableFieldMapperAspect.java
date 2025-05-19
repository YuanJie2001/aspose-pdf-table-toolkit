package com.vector.utils.pdf.aspect;

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
public class TableFieldMapperAspect <V>{

    // 缓存各个类的字段映射关系，避免重复反射
    private static final Map<Class<?>, Map<String, Field>> CLASS_FIELD_CACHE = new ConcurrentHashMap<>();

    // 默认日期格式化器
    private static final SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("yyyy");

    /**
     * 加载指定类中的字段映射关系
     *
     * @param clazz 需要加载映射关系的目标类
     * @return 映射关系Map，结构为[表字段名 -> 对应的Field对象]
     */
    public static <V> Map<String, Field> loadFieldMappings(Class<V> clazz){
        return CLASS_FIELD_CACHE.computeIfAbsent(clazz, cls -> {
            Map<String, Field> fieldMap = new HashMap<>();
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                // 预防跨页半个表格
                String val = field.getName();
                if(val.isBlank())return null;
                TableFieldMap annotation = field.getAnnotation(TableFieldMap.class);
                if (annotation != null) {
                    String tableFieldName = annotation.value();
                    fieldMap.put(tableFieldName, field);
                    log.debug("加载字段映射: {} -> {}", tableFieldName, val);
                }
            }
            log.debug("成功加载 {} 个字段映射", fieldMap.size());
            return fieldMap;
        });
    }


    /**
     * 使用注解方式将字段值映射到目标对象的对应属性（使用默认日期格式）
     *
     * @param target 需要设置属性的目标对象
     * @param key 表格中的原始字段名称
     * @param value 从表格中读取的原始字符串值
     * @param fieldCache 通过loadFieldMappings加载的字段映射缓存
     */
    public static void mapFieldByAnnotation(Object target, String key, String value,
                                            Map<String, Field> fieldCache) {
        mapFieldByAnnotation(target, key, value, fieldCache, DEFAULT_DATE_FORMAT);
    }

    /**
     * 使用注解方式将字段值映射到目标对象的对应属性
     *
     * @param target 需要设置属性的目标对象
     * @param key 表格中的原始字段名称
     * @param value 从表格中读取的原始字符串值
     * @param fieldCache 通过loadFieldMappings加载的字段映射缓存
     * @param dateFormat 用于日期类型转换的自定义格式化器
     */
    public static void mapFieldByAnnotation(Object target, String key, String value,
                                            Map<String, Field> fieldCache, SimpleDateFormat dateFormat) {
        Field field = fieldCache.get(key);
        if (field == null) {
            log.warn("未找到映射字段: {}", key);
            return;
        }

        try {
            // 类型转换处理流程
            Class<?> fieldType = field.getType();
            Object convertedValue = null;
            // 根据字段类型进行值转换
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
            // 设置转换后的值到目标字段
            if (convertedValue != null) {
                field.set(target, convertedValue);
            }
        } catch (Exception e) {
            log.error("设置字段值失败: {} = {}, 原因: {}", field.getName(), value, e.getMessage());
        }
    }
    /**
     * 数值类型解析器
     *
     * @param type 目标数值类型（支持Long/Integer/Double及其基本类型）
     * @param value 待解析的字符串数值
     * @return 解析后的数值对象，解析失败时返回null
     */
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

    /**
     * 布尔值解析器（支持非标准布尔值转换）
     *
     * @param value 待解析的字符串值
     * @return 解析后的布尔值（非"true"/"false"时会记录警告）
     */
    private static boolean parseBoolean(String value) {
        boolean result = Boolean.parseBoolean(value);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            log.warn("非标准布尔值转换: {} -> {}", value, result);
        }
        return result;
    }

}
