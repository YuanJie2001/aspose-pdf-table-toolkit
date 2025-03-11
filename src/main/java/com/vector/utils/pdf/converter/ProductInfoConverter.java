package com.vector.utils.pdf.converter;

import com.vector.utils.pdf.entity.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.vector.utils.pdf.AbstractTextMappingTemplate;
import java.util.Date;

/**
 * 映射到ProductInfo对象
 * @author YuanJie
 * @ClassName ProductInfoMapping
 * @description: TODO
 * @date 2025/3/5 14:22
 */
@Slf4j
@Component
public class ProductInfoConverter extends AbstractTextMappingTemplate {


    @Override
    protected void doMapping(StringBuilder tableContent) {
        try {
            // 清理和预处理表格内容
            String content = cleanTableContent(tableContent);
            
            // 提取键值对并映射到对象
            ProductInfo productInfo = super.mapToEntity(content, ProductInfo.class);

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
    
    /**
     * 清理和预处理表格内容
     * 
     * @param tableContent 原始表格内容
     * @return 处理后的内容字符串
     */
    private String cleanTableContent(StringBuilder tableContent) {
        String content = tableContent.toString();
        content = content.replace("入职申请表一|", "");
        content = content.replace("入职申请表二|", "");
        return content;
    }
    


    @Override
    protected boolean startWith(String str) {
        // 同时支持表一和合并后的表格
        return str.startsWith("入职申请表一|单位");
    }

    @Override
    protected boolean endWith(String str) {
        // 同时支持表二的结尾和合并后结尾
        return str.endsWith("声断恒山之浦。|");
    }
}
