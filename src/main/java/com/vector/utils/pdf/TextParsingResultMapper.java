package com.vector.utils.pdf;

import com.vector.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 解析内容映射到结构化对象
 * 可继承该方法，自定义实现解析器
 *
 * @author YuanJie
 * @ClassName analyzeMappingHandler
 * @description:
 * @date 2025/3/5 14:06
 */
@Slf4j
public abstract class TextParsingResultMapper {

    // 改为非静态成员，由Spring管理生命周期
    private static volatile List<TextParsingResultMapper> handlerCache;

    // 通过静态方法访问（线程安全初始化）
    public static List<TextParsingResultMapper> getHandlers() {
        if (handlerCache == null) {
            synchronized (TextParsingResultMapper.class) {
                if (handlerCache == null) {
                    // 延迟初始化，确保Spring容器已加载
                    ApplicationContext context = SpringContextUtil.getApplicationContext();
                    if (context == null) {
                        log.error("Spring上下文未初始化，无法获取处理器实例");
                        return Collections.emptyList();
                    }

                    Map<String, TextParsingResultMapper> beans = context.getBeansOfType(TextParsingResultMapper.class);
                    log.info("找到 {} 个ParsingMappingHandler实现类", beans.size());
                    List<TextParsingResultMapper> handlers = new ArrayList<>(beans.values());
                    // 安全校验
                    if (handlers.isEmpty()) {
                        throw new IllegalStateException("未发现任何有效的表格映射处理器");
                    }
                    handlerCache = List.copyOf(handlers);
                }
            }
        }
        return handlerCache;
    }

    // 模板方法定义处理流程
    public final void processTable(StringBuilder sb) {
        if (validateTableStructure(sb) && shouldHandle(sb)) {
            log.info("解析结果：{}", sb);
            // 执行映射
            doMapping(sb);
            // 后置处理
            postProcess();
        }


    }

    private void removeMagicNumber(StringBuilder sb) {
        sb.delete(0, PdfTableExtractor.TABLE_MARK.length());
        sb.delete(sb.length() - PdfTableExtractor.TABLE_MARK.length(), sb.length());
    }

    // 必须实现的抽象方法（编译期强制约束）
    protected abstract void doMapping(StringBuilder tableContent);

    // 必须实现的匹配规则（防御性校验）
    protected abstract boolean startWith(String str);

    protected abstract boolean endWith(String str);


    // 公共校验逻辑
    private boolean validateTableStructure(StringBuilder sb) {
        String str = sb.toString();
        if (str.startsWith(PdfTableExtractor.TABLE_MARK) && str.endsWith(PdfTableExtractor.TABLE_MARK)) {
            removeMagicNumber(sb);
            return true;
        }
        return false;
    }

    // 钩子方法（可选的后处理）
    protected void postProcess() {
    }

    private boolean shouldHandle(StringBuilder sb) {
        String str = sb.toString();
        return startWith(str) && endWith(str);
    }
}
