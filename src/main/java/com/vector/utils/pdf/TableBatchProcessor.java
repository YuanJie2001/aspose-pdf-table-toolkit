package com.vector.utils.pdf;

import com.aspose.pdf.AbsorbedTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 表格批处理器
 * 负责收集表格并批量提交给映射处理器，支持跨页表格识别与合并
 *
 * @author YuanJie
 * @date 2025/3/10
 */
@Slf4j
@Component
public class TableBatchProcessor {

    /**
     * 表格缓冲队列容量（页数）
     */
    private static final int BUFFER_CAPACITY = 50;

    /**
     * 单页最大表格数量限制
     */
    private static final int MAX_TABLES_PER_PAGE = 10;

    /**
     * 总单元格数量限制
     */
    private static final int MAX_TOTAL_CELLS = 1000;

    /**
     * 表格相似度阈值（用于判断是否为跨页表格）
     */
    private static final double TABLE_SIMILARITY_THRESHOLD = 0.85;

    /**
     * 异常检测阈值（连续重复表格数量）
     */
    private static final int DUPLICATE_TABLE_THRESHOLD = 5;

    /**
     * 批处理线程池
     */
    private final ExecutorService executorService;

    /**
     * 表格缓冲队列（按页收集）
     */
    private final BlockingQueue<List<StringBuilder>> tableBufferQueue;

    /**
     * 表格类型计数器（用于异常检测）
     */
    private final Map<String, AtomicInteger> tableTypeCounter;

    /**
     * 跨页表格缓存（用于合并跨页表格）
     */
    private final Map<String, StringBuilder> crossPageTableCache;

    /**
     * 构造函数
     */
    public TableBatchProcessor() {
        // 使用虚拟线程池处理批量映射任务
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        // 初始化表格缓冲队列
        this.tableBufferQueue = new LinkedBlockingQueue<>(BUFFER_CAPACITY);
        // 初始化表格类型计数器
        this.tableTypeCounter = new ConcurrentHashMap<>();
        // 初始化跨页表格缓存
        this.crossPageTableCache = new ConcurrentHashMap<>();
    }


    /**
     * 添加表格到批处理队列
     *
     * @param pageIndex 页码索引
     * @param tables    页面中的表格列表
     */
    public void addPageTables(int pageIndex, List<AbsorbedTable> tables) {
        // 资源限制检查
        if (tables.size() > MAX_TABLES_PER_PAGE) {
            log.warn("页面{}表格数量超过限制: {}", pageIndex, tables.size());
            // 截取前MAX_TABLES_PER_PAGE个表格
            tables = tables.subList(0, MAX_TABLES_PER_PAGE);
        }

        // 处理当前页的表格
        List<StringBuilder> processedTables = new ArrayList<>();
        for (AbsorbedTable table : tables) {
            // 处理单个表格
            StringBuilder tableContent = PdfTableExtractor.processSingleTable(table);
            if (tableContent == null) continue;

            // 数据清洗
            PdfTableExtractor.cleanData(tableContent);

            // 检查是否为跨页表格
            String tableFingerprint = generateTableFingerprint(tableContent);
            if (isCrossPageTable(tableContent, tableFingerprint)) {
                // 合并跨页表格
                mergeCrossPageTable(tableContent, tableFingerprint);
            } else {
                // 异常检测（连续重复表格）
                if (isDuplicateTable(tableFingerprint)) {
                    log.warn("检测到连续重复表格类型: {}", tableFingerprint);
                    continue;
                }
                // 添加到处理队列
                processedTables.add(tableContent);
            }
        }

        // 将处理后的表格添加到缓冲队列
        if (!processedTables.isEmpty()) {
            try {
                // 尝试添加到队列，如果队列已满则提交当前队列中的所有表格
                if (!tableBufferQueue.offer(processedTables, 100, TimeUnit.MILLISECONDS)) {
                    log.info("缓冲队列已满，提交批处理任务");
                    submitBatchTask();
                    // 重新尝试添加
                    tableBufferQueue.put(processedTables);
                }
                log.info("队列解析内容为：{}", processedTables);
            } catch (InterruptedException e) {
                log.error("添加表格到缓冲队列失败: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 提交批处理任务
     */
    public void submitBatchTask() {
        List<List<StringBuilder>> batchTables = new ArrayList<>();
        tableBufferQueue.drainTo(batchTables);

        if (!batchTables.isEmpty()) {
            // 提交批处理任务
            executorService.submit(() -> processBatchTables(batchTables));
        }
    }

    /**
     * 处理批量表格
     *
     * @param batchTables 批量表格列表
     */
    private void processBatchTables(List<List<StringBuilder>> batchTables) {
        // 获取所有映射处理器
        List<TextParsingResultMapper> handlers = TextParsingResultMapper.getHandlers();

        // 处理每个表格
        for (List<StringBuilder> pageTables : batchTables) {
            for (StringBuilder tableContent : pageTables) {
                // 调用映射处理器处理表格
                for (TextParsingResultMapper handler : handlers) {
                    try {
                        handler.processTable(tableContent);
                    } catch (Exception e) {
                        log.error("表格处理失败,内容: {},原因: {}", tableContent, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 生成表格指纹（用于识别表格类型）
     *
     * @param tableContent 表格内容
     * @return 表格指纹
     */
    private String generateTableFingerprint(StringBuilder tableContent) {
        // 简单实现：使用表格前10个字符作为指纹
        int length = Math.min(tableContent.length(), 10);
        return tableContent.substring(0, length);
    }

    /**
     * 判断是否为跨页表格
     *
     * @param tableContent    当前表格内容（用于内容比较，如果需要更精确的比较）
     * @param tableFingerprint 表格指纹
     * @return 是否为跨页表格
     */
    private boolean isCrossPageTable(StringBuilder tableContent, String tableFingerprint) {
        // 检查是否有相似的表格在缓存中
        if (crossPageTableCache.isEmpty()) {
            return false;
        }

        // 基于指纹进行相似度比较
        for (Map.Entry<String, StringBuilder> entry : crossPageTableCache.entrySet()) {
            String cachedFingerprint = entry.getKey();
            // 计算相似度
            double similarity = calculateSimilarity(cachedFingerprint, tableFingerprint);
            if (similarity >= TABLE_SIMILARITY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并跨页表格
     *
     * @param tableContent    当前表格内容
     * @param tableFingerprint 表格指纹
     */
    private void mergeCrossPageTable(StringBuilder tableContent, String tableFingerprint) {
        // 查找匹配的缓存表格
        String matchedFingerprint = null;
        for (Map.Entry<String, StringBuilder> entry : crossPageTableCache.entrySet()) {
            String cachedFingerprint = entry.getKey();
            // 计算相似度
            double similarity = calculateSimilarity(cachedFingerprint, tableFingerprint);
            if (similarity >= TABLE_SIMILARITY_THRESHOLD) {
                matchedFingerprint = cachedFingerprint;
                break;
            }
        }

        if (matchedFingerprint != null) {
            // 合并表格内容
            StringBuilder cachedTable = crossPageTableCache.get(matchedFingerprint);
            // 移除标记后合并
            String content = tableContent.toString();
            cachedTable.insert(cachedTable.length(), content);
            log.info("合并跨页表格: {}", matchedFingerprint);

            // 更新缓存中的表格内容
            crossPageTableCache.put(matchedFingerprint, cachedTable);
        } else {
            // 添加到缓存
            crossPageTableCache.put(tableFingerprint, new StringBuilder(tableContent));
        }
    }

    /**
     * 计算两个字符串的相似度（简化的实现）
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return 相似度（0-1）
     */
    private double calculateSimilarity(String str1, String str2) {
        // 简化实现：计算公共子串长度占较短字符串的比例
        int minLength = Math.min(str1.length(), str2.length());
        int maxCommonLength = 0;

        // 查找最长公共子串
        for (int i = 0; i < minLength; i++) {
            if (str1.charAt(i) == str2.charAt(i)) {
                maxCommonLength++;
            }
        }

        return (double) maxCommonLength / minLength;
    }

    /**
     * 判断是否为连续重复表格
     *
     * @param tableFingerprint 表格指纹
     * @return 是否为连续重复表格
     */
    private boolean isDuplicateTable(String tableFingerprint) {
        AtomicInteger counter = tableTypeCounter.computeIfAbsent(tableFingerprint, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        // 定期重置计数器（避免长期累积）
        if (count > DUPLICATE_TABLE_THRESHOLD * 2) {
            counter.set(0);
        }
        // 如果连续出现超过阈值，则判定为重复表格
        return count > DUPLICATE_TABLE_THRESHOLD;
    }

    /**
     * 关闭处理器
     */
    public void shutdown() {
        // 提交剩余的表格
        submitBatchTask();
        // 处理跨页表格缓存中的表格
        processCrossPageTables();
        // 关闭线程池
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 处理跨页表格缓存中的表格
     */
    private void processCrossPageTables() {
        if (!crossPageTableCache.isEmpty()) {
            log.info("处理跨页表格缓存: {} 个表格", crossPageTableCache.size());

            // 创建一个新的列表来存储跨页表格
            List<StringBuilder> crossPageTables = new ArrayList<>();
            // 将跨页表格添加到批处理队列
            List<List<StringBuilder>> batchTables = new ArrayList<>();
            batchTables.add(crossPageTables);
            processBatchTables(batchTables);

            // 清空缓存
            crossPageTableCache.clear();
        }
    }
}
