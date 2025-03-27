package com.vector.utils.pdf;

import com.aspose.pdf.*;
import com.vector.config.WordAuthLincense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;


/**
 * pdf 表格解析工具类，提供从PDF文档中提取并处理表格数据的功能
 *
 * @author YuanJie
 * @ClassName PdfTableExtractor
 * @description: 本类通过解析PDF文档结构，识别表格数据并进行结构化处理，支持多线程环境下的表格解析操作
 * @date 2025/2/27 15:36
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfTableParsingEngine {

    /**
     * 正则去除空格和换行符的正则表达式
     */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * 表格批处理器实例
     */
    private final TableBatchProcessor batchProcessor;

    /**
     * 公开解析方法
     * 程序入口，处理PDF文档表格解析任务。负责初始化授权、加载文档，并协调多页面处理流程
     *
     * @param path 需要解析的PDF文件路径，要求是有效的本地文件系统路径
     * @throws IOException 当发生文件读写异常或文档操作异常时抛出
     *                     具体可能发生在：
     *                     - 文件路径无效或权限不足
     *                     - 文档加载/解析失败
     *                     - 资源关闭异常
     */
    public void tableAnalyze(String path) {
        // 初始化文档处理授权许可
        WordAuthLincense.setAuthLicense();
        // 使用try-with-resources自动管理文件资源
        try (FileInputStream fileStream = new FileInputStream(path);
             Document pdfDocument = new Document(fileStream)) {

            // 遍历文档所有页面进行表格处理
            for (int i = 1; i <= pdfDocument.getPages().size(); i++) {
                Page page = pdfDocument.getPages().get_Item(i);
                processPageTables(page, i);
            }

            // 提交剩余的表格并关闭批处理器
            batchProcessor.submitBatchTask();
            // 不再调用shutdown方法，避免关闭线程池
            // batchProcessor.shutdown();

        } catch (IOException e) {
            log.error("解析异常：{}", e.getMessage());
        }
    }


    /**
     * 处理页面中的表格数据。通过表格吸收器获取页面内所有表格对象，
     * 并协调后续的表格处理、数据清洗和结果映射流程
     *
     * @param page 需要处理的PDF页面对象，包含文本布局和表格结构信息
     * @param pageIndex 页码索引
     */
    private void processPageTables(Page page, int pageIndex) {

        // 创建表格扫描器并解析页面内容
        // 注意：可调用tableAbsorber.removeAllTables()清理残留表格数据
        TableAbsorber tableAbsorber = new TableAbsorber();
        tableAbsorber.visit(page);
        List<AbsorbedTable> tables = tableAbsorber.getTableList();
        if (CollectionUtils.isEmpty(tables)) return;

        // 将表格添加到批处理器
        batchProcessor.addPageTables(pageIndex, tables);
    }


    /**
     * 执行表格数据清洗操作。包括：
     * 1. 移除所有空白字符和换行符
     * 2. 在数据首尾添加表格标记用于后续识别
     *
     * @param builder 包含原始表格数据的字符串构建器，要求非空
     */
    protected static void cleanData(StringBuilder builder) {
        Objects.requireNonNull(builder, "StringBuilder must not be null");

        // 删除空格和换行符（使用预编译正则）
        String cleaned = WHITESPACE_PATTERN.matcher(builder.toString()).replaceAll("");
        builder.setLength(0);
        builder.append(cleaned);
    }
}
