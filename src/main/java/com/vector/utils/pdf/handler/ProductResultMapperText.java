package com.vector.utils.pdf.handler;

import com.vector.utils.pdf.annotation.TableFieldMap;
import com.vector.utils.pdf.entity.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.vector.utils.pdf.TextParsingResultMapper;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 映射到ProductInfo对象
 * @author YuanJie
 * @ClassName ProductInfoMapping
 * @description: TODO
 * @date 2025/3/5 14:22
 */
@Slf4j
@Component
public class ProductResultMapperText extends TextParsingResultMapper {

    // 缓存字段映射关系，避免重复反射
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    // 日期格式化器
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy");
    // 静态初始化块，预加载字段映射
    static {
        loadFieldMappings();
    }

    /**
     * 加载ProductInfo类中的字段映射关系
     */
    private static void loadFieldMappings() {
        Field[] fields = ProductInfo.class.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            TableFieldMap annotation = field.getAnnotation(TableFieldMap.class);
            if (annotation != null) {
                String tableFieldName = annotation.value();
                FIELD_CACHE.put(tableFieldName, field);
                log.debug("加载字段映射: {} -> {}", tableFieldName, field.getName());
            }
        }
        log.info("成功加载 {} 个字段映射", FIELD_CACHE.size());
    }


    @Override
    protected void doMapping(StringBuilder tableContent) {
        try {
            ProductInfo productInfo = new ProductInfo();
            // 解析表格内容
            String content = tableContent.toString();

            // 使用正则表达式提取键值对
            Pattern pattern = Pattern.compile("([^|]+)\\|([^|]+)\\|");
            Matcher matcher = pattern.matcher(content);

            while (matcher.find()) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2).trim();

                // 使用注解方式映射字段
                mapFieldByAnnotation(productInfo, key, value);
            }

            // 设置创建时间
            productInfo.setCreateAt(new Date());

            // 输出解析结果
            log.info("成功解析ProductInfo: {}", productInfo);

            // 这里可以添加保存到数据库或其他处理逻辑
        } catch (Exception e) {
            log.error("映射ProductInfo失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean startWith(String str) {
        return str.startsWith("单位|xxxxx1111|");
    }

    @Override
    protected boolean endWith(String str) {
        return str.endsWith("酷酷酷酷酷酷酷酷|");
    }


    /**
     * 使用注解方式将字段值映射到ProductInfo对象的对应属性
     *
     * @param productInfo ProductInfo对象
     * @param key 表格中的字段名
     * @param value 字段值
     */
    private void mapFieldByAnnotation(ProductInfo productInfo, String key, String value) {
        // 从缓存中获取对应的字段
        Field field = FIELD_CACHE.get(key);
        if (field == null) {
            log.warn("未找到映射字段: {}", key);
            return;
        }

        try {
            // 根据字段类型进行转换并设置值
            Class<?> fieldType = field.getType();

            if (String.class.equals(fieldType)) {
                field.set(productInfo, value);
            } else if (Date.class.equals(fieldType)) {
                try {
                    Date date = DATE_FORMAT.parse(value);
                    field.set(productInfo, date);
                } catch (ParseException e) {
                    log.error("日期解析失败: {}", value, e);
                }
            } else if (Long.class.equals(fieldType)) {
                try {
                    Long longValue = Long.parseLong(value);
                    field.set(productInfo, longValue);
                } catch (NumberFormatException e) {
                    log.error("数字解析失败: {}", value, e);
                }
            } else if (Integer.class.equals(fieldType)) {
                try {
                    Integer intValue = Integer.parseInt(value);
                    field.set(productInfo, intValue);
                } catch (NumberFormatException e) {
                    log.error("数字解析失败: {}", value, e);
                }
            } else {
                log.warn("不支持的字段类型: {}", fieldType.getName());
            }
        } catch (Exception e) {
            log.error("设置字段值失败: {} = {}, 原因: {}", field.getName(), value, e.getMessage());
        }
    }

}
