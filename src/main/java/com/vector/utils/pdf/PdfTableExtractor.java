package com.vector.utils.pdf;

import com.aspose.pdf.*;
import com.vector.config.WordAuthLincense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

;

/**
 * pdf 表格解析
 *
 * @author YuanJie
 * @ClassName Demo
 * @description: TODO
 * @date 2025/2/27 15:36
 */
@Slf4j
public class PdfTableExtractor {
    /**
     * 表格解析时，每个单元格内容预估字符数，用于初始化表格缓冲区
     */
    private static final int ESTIMATED_CELL_SIZE = 50;
    /**
     * 表格标记
     */
    protected static final String TABLE_MARK = "0x1315749";
    /**
     * 正则去除空格和换行符的正则表达式
     */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    /**
     * 公开解析方法
     * 程序入口，处理PDF文档表格解析任务
     *
     * @throws IOException 当发生文件读写异常或文档操作异常时抛出
     *                     具体可能发生在：
     *                     - 文件路径无效或权限不足
     *                     - 文档加载/解析失败
     *                     - 资源关闭异常
     */
    public static void tableAnalyze(String path) {
        // 初始化文档处理授权许可
        WordAuthLincense.setAuthLicense();
        // 使用try-with-resources自动管理文件资源
        try (FileInputStream fileStream = new FileInputStream(path);
             Document pdfDocument = new Document(fileStream)) {

            // 创建表格处理器实例
            // 遍历文档所有页面进行表格处理
            for (Page page : pdfDocument.getPages()) {
                processPageTables(page);
            }
        } catch (IOException e) {
            log.error("解析异常：{}", e.getMessage());
        }
    }


    /**
     * 处理页面中的表格数据
     *
     * @param page 需要处理的页面对象，包含待解析的页面内容
     */
    private static void processPageTables(Page page) {

        // 创建表格扫描器并解析页面内容
        // 注意：可调用tableAbsorber.removeAllTables()清理残留表格数据
        TableAbsorber tableAbsorber = new TableAbsorber();
        tableAbsorber.visit(page);
        List<AbsorbedTable> tables = tableAbsorber.getTableList();
        if (CollectionUtils.isEmpty(tables)) return;

        // 迭代处理所有解析到的表格
        for (AbsorbedTable table : tables) {
            StringBuilder builder = processSingleTable(table);
            if (builder == null) continue;
            // 数据清洗
            cleanData(builder);
            // 将解析结果传递给映射处理器
            for (TextParsingResultMapper handler : TextParsingResultMapper.getHandlers()) {
                try {
                    handler.processTable(builder);
                } catch (Exception e) {
                    log.error("表格处理失败,内容: {},原因: {}", builder, e.getMessage());
                }
            }
        }

    }


    /**
     * 处理单个表格数据，将表格内容拼接为字符串并记录日志
     * 函数在多线程环境下运行，通过使用局部变量保障线程安全。处理过程包括：
     * 1. 初始化表格内容缓冲区
     * 2. 遍历表格行和单元格
     * 3. 处理单元格内容并拼接表格结构符号
     *
     * @param table 需要处理的表格对象，包含行和单元格数据
     */
    private static StringBuilder processSingleTable(AbsorbedTable table) {
        // 多线程，避免线程安全问题。多线程表格解析
        // 缓存行集合
        List<AbsorbedRow> rows = table.getRowList();

        /* 初始化表格缓冲区，根据首行单元格数量预估初始容量 */
        StringBuilder tableContext = new StringBuilder(rows.size() * rows.getFirst().getCellList().size() * ESTIMATED_CELL_SIZE);

        if (CollectionUtils.isEmpty(rows)) return null;

        /* 行处理：遍历所有数据行 */
        for (AbsorbedRow row : rows) {
            // 缓存单元格集合
            List<AbsorbedCell> cells = row.getCellList();
            if (CollectionUtils.isEmpty(cells)) continue;

            /* 单元格处理：拼接单元格内容并添加列分隔符 */
            for (AbsorbedCell cell : cells) {
                processCellContext(cell, tableContext);
                tableContext.append("|");
            }
            tableContext.append("\n");
        }

        /* 最终结果输出：将拼接完成的表格内容写入日志 */
        return tableContext;
    }


    /**
     * 处理单元格文本内容并聚合到表格上下文中
     *
     * @param cell         被处理的单元格对象，包含文本片段集合
     * @param tableContext 用于存储聚合后文本内容的字符串构建器
     */
    private static void processCellContext(AbsorbedCell cell, StringBuilder tableContext) {
        // 获取并校验单元格文本片段集合
        TextFragmentCollection fragments = cell.getTextFragments();
        if (fragments == null) return;

        // 遍历所有文本块（段落结构）
        for (TextFragment fragment : fragments) {
            /*
             * 聚合单元格内所有文本样式片段：
             * 1. 遍历段落中的每个文本片段
             * 2. 将文本内容追加到表格上下文
             */
            for (TextSegment seg : fragment.getSegments()) {
                tableContext.append(seg.getText());
            }
        }
    }

    private static void cleanData(StringBuilder builder) {
        Objects.requireNonNull(builder, "StringBuilder must not be null");

        // 删除空格和换行符（使用预编译正则）
        String cleaned = WHITESPACE_PATTERN.matcher(builder.toString()).replaceAll("");
        builder.setLength(0);
        builder.append(cleaned);

        // 添加标记
        builder.insert(0, TABLE_MARK);
        builder.append(TABLE_MARK);
    }
}
