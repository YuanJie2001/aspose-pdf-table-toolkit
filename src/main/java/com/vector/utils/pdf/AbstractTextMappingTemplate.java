package com.vector.utils.pdf;

import com.vector.utils.SpringContextUtil;
import com.vector.utils.pdf.aspect.TableFieldMapperAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public abstract class AbstractTextMappingTemplate {

    // 改为非静态成员，由Spring管理生命周期
    private static volatile List<AbstractTextMappingTemplate> handlerCache;
    // 键值对提取正则表达式
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("([^|]+)\\|([^|]+)\\|");
    // 日期格式化器
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy");

    /**
     * 获取所有处理器实现（线程安全初始化）
     * 采用双重检查锁模式确保单次初始化，延迟加载直到首次调用
     *
     * @return 不可修改的处理器列表（Spring容器管理的bean实例）
     * @throws IllegalStateException 当未找到任何有效处理器时抛出
     */
    public static List<AbstractTextMappingTemplate> getHandlers() {
        // 双重检查锁实现线程安全初始化
        if (handlerCache == null) {
            synchronized (AbstractTextMappingTemplate.class) {
                if (handlerCache == null) {
                    // 延迟获取Spring上下文，确保容器初始化完成
                    ApplicationContext context = SpringContextUtil.getApplicationContext();
                    if (context == null) {
                        log.error("Spring上下文未初始化，无法获取处理器实例");
                        return Collections.emptyList();
                    }
                    // 获取所有子类实现实例
                    Map<String, AbstractTextMappingTemplate> beans = context.getBeansOfType(AbstractTextMappingTemplate.class);
                    log.info("找到 {} 个ParsingMappingHandler实现类", beans.size());
                    List<AbstractTextMappingTemplate> handlers = new ArrayList<>(beans.values());
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
        if (shouldHandle(sb)) {
            log.info("解析结果：{}", sb);
            doMapping(sb);// 执行映射
            postProcess(); // 钩子方法调用
        }


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


    /**
     * 提取键值对并映射到ProductInfo对象
     *
     * @param content 预处理后的内容
     * @param clazz 待填充的产品信息对象
     * TODO移除缓存map
     */
    protected <V> V mapToEntity(String content, Class<V> clazz) {
        Matcher matcher = KEY_VALUE_PATTERN.matcher(content);
        V target = null;
        
        // 先创建目标对象实例
        try {
            target = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("无法创建目标对象实例", e);
        }
        
        // 加载字段映射关系（只需加载一次）
        Map<String, Field> loadFieldMappings = TableFieldMapperAspect.loadFieldMappings(clazz);
        
        // 遍历所有匹配的键值对
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();

            // 执行安全转义处理
            key = escapeKey(key);
            value = escapeValue(value);

            // 映射字段到对象
            mapField(target, loadFieldMappings, key, value);
            log.debug("映射字段: {} -> {}", key, value);
        }
        return target;
    }

    /**
     * 对键进行安全转义处理
     *
     * @param key 原始键
     * @return 转义后的键
     */
    protected String escapeKey(String key) {
        return StringEscapeUtil.escapeHtml(key);
    }

    /**
     * 对值进行上下文感知的安全转义处理
     *
     * @param value 原始值
     * @return 转义后的值
     */
    protected String escapeValue(String value) {
        return StringEscapeUtil.escapeByContext(value, StringEscapeUtil.ContextType.HTML);
    }

    /**
     * 将键值对映射到对象字段
     *
     * @param target 目标类
     * @param mapCache 字段映射缓存
     * @param key 字段键名
     * @param value 字段值
     */
    protected void mapField(Object target, Map<String, Field> mapCache, String key, String value) {
        TableFieldMapperAspect.mapFieldByAnnotation(target, key, value, mapCache, DATE_FORMAT);
    }
}
