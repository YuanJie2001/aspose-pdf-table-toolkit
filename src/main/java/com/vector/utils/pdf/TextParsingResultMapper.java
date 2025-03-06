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
 * @description: 文本解析结果映射抽象类，提供通用处理流程和扩展点
 * @date 2025/3/5 14:06
 */
@Slf4j
public abstract class TextParsingResultMapper {

    // 改为非静态成员，由Spring管理生命周期
    private static volatile List<TextParsingResultMapper> handlerCache;

    /**
     * 获取所有处理器实现（线程安全初始化）
     * 采用双重检查锁模式确保单次初始化，延迟加载直到首次调用
     *
     * @return 不可修改的处理器列表（Spring容器管理的bean实例）
     * @throws IllegalStateException 当未找到任何有效处理器时抛出
     */
    public static List<TextParsingResultMapper> getHandlers() {
        // 双重检查锁实现线程安全初始化
        if (handlerCache == null) {
            synchronized (TextParsingResultMapper.class) {
                if (handlerCache == null) {
                    // 延迟获取Spring上下文，确保容器初始化完成
                    ApplicationContext context = SpringContextUtil.getApplicationContext();
                    if (context == null) {
                        log.error("Spring上下文未初始化，无法获取处理器实例");
                        return Collections.emptyList();
                    }
                    // 获取所有子类实现实例
                    Map<String, TextParsingResultMapper> beans = context.getBeansOfType(TextParsingResultMapper.class);
                    log.info("找到 {} 个ParsingMappingHandler实现类", beans.size());
                    List<TextParsingResultMapper> handlers = new ArrayList<>(beans.values());
                    // 防御性校验确保至少有一个处理器
                    if (handlers.isEmpty()) {
                        throw new IllegalStateException("未发现任何有效的表格映射处理器");
                    }
                    handlerCache = List.copyOf(handlers);
                }
            }
        }
        return handlerCache;
    }

    /**
     * 模板方法定义标准处理流程（final防止子类覆盖流程）
     * 1. 验证表格结构有效性
     * 2. 执行子类匹配规则判断
     * 3. 执行具体映射逻辑
     * 4. 执行可选的后处理操作
     *
     * @param sb 待处理的表格内容容器
     */
    public final void processTable(StringBuilder sb) {
        // 执行标准处理流程
        if (validateTableStructure(sb) && shouldHandle(sb)) {
            log.info("解析结果：{}", sb);
            doMapping(sb);// 执行映射
            postProcess(); // 钩子方法调用
        }


    }
    /**
     * 清理表格内容中的特殊标记
     * 删除开头和结尾的TABLE_MARK标记（各删除PdfTableExtractor.TABLE_MARK.length()长度）
     */
    private void removeMagicNumber(StringBuilder sb) {
        sb.delete(0, PdfTableExtractor.TABLE_MARK.length());
        sb.delete(sb.length() - PdfTableExtractor.TABLE_MARK.length(), sb.length());
    }

    /**
     * 抽象映射方法（强制子类实现）
     * 实现具体的表格内容到结构化对象的转换逻辑
     */
    protected abstract void doMapping(StringBuilder tableContent);

    /**
     * 起始匹配规则（防御性校验）
     * 用于判断是否处理当前表格内容
     */
    protected abstract boolean startWith(String str);
    /**
     * 结束匹配规则（防御性校验）
     * 用于判断是否处理当前表格内容
     */
    protected abstract boolean endWith(String str);


    /**
     * 验证表格结构有效性
     * 检查是否包含标准标记头尾，验证通过后清理标记
     *
     * @return true表示具有有效的表格结构
     */
    private boolean validateTableStructure(StringBuilder sb) {
        String str = sb.toString();
        if (str.startsWith(PdfTableExtractor.TABLE_MARK) && str.endsWith(PdfTableExtractor.TABLE_MARK)) {
            removeMagicNumber(sb);
            return true;
        }
        return false;
    }

    /**
     * 后处理钩子方法（可选扩展点）
     * 子类可覆盖该方法实现自定义后处理逻辑
     */
    protected void postProcess() {
    }
    /**
     * 判断是否应该处理当前内容
     * 综合子类定义的起始/结束匹配规则
     */
    private boolean shouldHandle(StringBuilder sb) {
        String str = sb.toString();
        return startWith(str) && endWith(str);
    }
}
