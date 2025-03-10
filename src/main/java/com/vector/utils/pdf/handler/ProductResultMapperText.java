package com.vector.utils.pdf.handler;

import com.vector.utils.pdf.aspect.TableFieldMapperAspect;
import com.vector.utils.pdf.entity.ProductInfo;
import com.vector.utils.pdf.StringEscapeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.vector.utils.pdf.TextParsingResultMapper;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private static final Map<String, Field> FIELD_CACHE = TableFieldMapperAspect.loadFieldMappings(ProductInfo.class);
    // 日期格式化器
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy");

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
                
                // 执行内容转义处理，防止注入攻击
                // 对键使用HTML转义（防止XSS攻击）
                key = StringEscapeUtil.escapeHtml(key);
                // 对值使用上下文感知的转义（根据内容特征选择合适的转义方式）
                value = StringEscapeUtil.escapeByContext(value, StringEscapeUtil.ContextType.HTML);
                
                // 使用注解方式映射字段
                TableFieldMapperAspect.mapFieldByAnnotation(productInfo, key, value, FIELD_CACHE, DATE_FORMAT);
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
        // 同时支持入职申请表一和合并后的表格
        return str.startsWith("入职申请表一|单位|");
    }

    @Override
    protected boolean endWith(String str) {
        // 同时支持入职申请表二的结尾和原有结尾
        return str.endsWith("声断恒山之浦。|");
    }
}
