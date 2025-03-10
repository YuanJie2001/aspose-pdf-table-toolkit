package com.vector.utils.pdf;

import com.aspose.pdf.AbsorbedTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
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
     * 列标识前缀
     */
    private static final String COLUMN_PREFIX = "col:";
    
    /**
     * 最大缓存条目数
     */
    private static final int MAX_CACHE_ENTRIES = 1000;

    /**
     * 矢量特征维度
     */
    private static final int VECTOR_DIMENSION = 32;

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
    private final Map<String, CacheEntry> crossPageTableCache;

    /**
     * 缓存清理调度器
     */
    private final ScheduledExecutorService cacheCleanupScheduler;

    /**
     * 缓存条目有效期（毫秒）
     */
    private static final long CACHE_ENTRY_TTL = 5 * 60 * 1000; // 5分钟

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
        // 初始化缓存清理调度器
        this.cacheCleanupScheduler = Executors.newScheduledThreadPool(1);
        // 启动定时清理任务
        this.cacheCleanupScheduler.scheduleAtFixedRate(this::cleanupCrossPageTableCache, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 缓存条目类（包含内容和时间戳）
     */
    private static class CacheEntry {
        StringBuilder content;
        long lastAccessTime;

        public CacheEntry(StringBuilder content) {
            this.content = content;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    /**
     * 清理跨页表格缓存（增强版）
     */
    private void cleanupCrossPageTableCache() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredKeys = new ArrayList<>();

        for (Map.Entry<String, CacheEntry> entry : crossPageTableCache.entrySet()) {
            if (currentTime - entry.getValue().lastAccessTime > CACHE_ENTRY_TTL) {
                expiredKeys.add(entry.getKey());
            }
        }

        // 限制缓存条目数量
        if (crossPageTableCache.size() > MAX_CACHE_ENTRIES) {
            expiredKeys.addAll(crossPageTableCache.keySet());
        }

        for (String key : expiredKeys) {
            crossPageTableCache.remove(key);
            log.info("清理过期缓存条目: {}", key);
        }
    }

    /**
     * 添加表格到批处理队列
     *
     * @param pageIndex 页码索引
     * @param tables    页面中的表格列表
     */
    public void addPageTables(int pageIndex, List<AbsorbedTable> tables) {
        // 资源限制检查 一页10个表格
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

            // 生成表格指纹
            String tableFingerprint = generateTableFingerprint(tableContent);

            // 将表格指纹和内容存储到跨页表格缓存中
            crossPageTableCache.putIfAbsent(tableFingerprint, new CacheEntry(new StringBuilder(tableContent)));
            // 更新缓存条目的最后访问时间
            crossPageTableCache.get(tableFingerprint).updateLastAccessTime();

            // 检查是否为跨页表格
            if (isCrossPageTable(tableFingerprint)) {
                // 合并跨页表格
                tableContent = mergeCrossPageTable(tableContent, tableFingerprint);
            } else {
                // 异常检测（连续重复表格）
                if (isDuplicateTable(tableFingerprint)) {
                    log.warn("检测到连续重复表格类型: {}", tableFingerprint);
                    continue;
                }
            }
            // 添加到处理队列
            processedTables.add(tableContent);
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
        // 综合表格样式、列特征和内容生成指纹
        int length = Math.min(tableContent.length(), 10); // 增加指纹长度
        String contentPart = tableContent.substring(0, length);
        String stylePart = extractTableStyleFeatures(tableContent); // 提取样式特征
        return contentPart + "|" + stylePart;
    }

    /**
     * 提取表格样式特征（如列数、表头信息等）
     *
     * @param tableContent 表格内容
     * @return 样式特征字符串
     */
    private String extractTableStyleFeatures(StringBuilder tableContent) {
        // 示例：提取列数作为样式特征
        int columnCount = countColumns(tableContent);
        return COLUMN_PREFIX + columnCount;
    }

    /**
     * 计算两个字符串的相似度（增强版）
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return 相似度（0-1）
     */
    private double calculateSimilarity(String str1, String str2) {
        // 计算内容相似度
        double contentSimilarity = calculateContentSimilarity(str1, str2);
        // 计算样式相似度
        double styleSimilarity = calculateStyleSimilarity(str1, str2);
        // 综合相似度
        return (contentSimilarity + styleSimilarity) / 2;
    }

    /**
     * 计算内容相似度（基于矢量相似度）
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return 内容相似度
     */
    private double calculateContentSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            throw new IllegalArgumentException("输入字符串不能为空");
        }
        
        // 将字符串转换为特征向量
        double[] vector1 = stringToVector(str1);
        double[] vector2 = stringToVector(str2);
        
        // 计算余弦相似度
        return cosineSimilarity(vector1, vector2);
    }
    
    /**
     * 将字符串转换为特征向量
     *
     * @param str 输入字符串
     * @return 特征向量
     */
    private double[] stringToVector(String str) {
        // 初始化特征向量
        double[] vector = new double[VECTOR_DIMENSION];
        
        // 创建字符频率映射
        Map<Character, Integer> charFrequency = new HashMap<>();
        
        // 统计字符频率
        for (char c : str.toCharArray()) {
            charFrequency.put(c, charFrequency.getOrDefault(c, 0) + 1);
        }
        
        // 将字符频率映射到特征向量
        for (char c : charFrequency.keySet()) {
            int index = Math.abs(c) % VECTOR_DIMENSION;
            vector[index] += charFrequency.get(c);
        }
        
        // 归一化向量
        normalizeVector(vector);
        
        return vector;
    }
    
    /**
     * 归一化向量
     *
     * @param vector 输入向量
     */
    private void normalizeVector(double[] vector) {
        double magnitude = 0.0;
        
        // 计算向量模长
        for (double value : vector) {
            magnitude += value * value;
        }
        magnitude = Math.sqrt(magnitude);
        
        // 归一化向量
        if (magnitude > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= magnitude;
            }
        }
    }
    
    /**
     * 计算余弦相似度
     *
     * @param vector1 向量1
     * @param vector2 向量2
     * @return 余弦相似度
     */
    private double cosineSimilarity(double[] vector1, double[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("向量维度不匹配");
        }
        
        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            magnitude1 += vector1[i] * vector1[i];
            magnitude2 += vector2[i] * vector2[i];
        }
        
        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);
        
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        } else {
            return dotProduct / (magnitude1 * magnitude2);
        }
    }

    /**
     * 计算样式相似度
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return 样式相似度
     * 由于匹配机制基于表格前行，在第一表格前行可能分格多个单元格。因此一般认为取范围最合适
     */
    private double calculateStyleSimilarity(String str1, String str2) {
        // 示例：比较列数是否相同
        int columns1 = extractColumnCount(str1);
        int columns2 = extractColumnCount(str2);
        return Math.min(columns1, columns2) <= (Math.max(columns1, columns2) << 1) ? 1.0 : 0.0;
    }

    /**
     * 提取列数
     *
     * @param fingerprint 指纹字符串
     * @return 列数
     */
    private int extractColumnCount(String fingerprint) {
        String[] parts = fingerprint.split("\\|");
        for (String part : parts) {
            if (part.startsWith(COLUMN_PREFIX)) {
                try {
                    return Integer.parseInt(part.substring(COLUMN_PREFIX.length()));
                } catch (NumberFormatException e) {
                    log.error("解析列数失败: {}", part, e);
                }
            }
        }
        return 0;
    }

    /**
     * 计算表格列数
     *
     * @param tableContent 表格内容
     * @return 列数
     */
    private int countColumns(StringBuilder tableContent) {
        // 按行分割表格内容
        String[] rows = tableContent.toString().split("\n");
        if (rows.length == 0) {
            return 0;
        }
        // 按列分隔符 '|' 分割第一行，计算列数
        String[] columns = rows[0].split("\\|");
        return columns.length - 1; // 减去最后一个空列，因为行末尾有一个分隔符
    }

    /**
     * 判断是否为跨页表格
     *
     * @param tableFingerprint 表格指纹
     * @return 是否为跨页表格
     */
    private boolean isCrossPageTable(String tableFingerprint) {
        // 一般情况下的跨页表格检测
        for (Map.Entry<String, CacheEntry> entry : crossPageTableCache.entrySet()) {
            String cachedFingerprint = entry.getKey();
            if (!cachedFingerprint.equals(tableFingerprint)) { // 排除自身
                try {
                    double similarity = calculateSimilarity(cachedFingerprint, tableFingerprint);
                    if (similarity >= TABLE_SIMILARITY_THRESHOLD) {
                        log.info("检测到跨页表格，相似度: {}", similarity);
                        return true;
                    }
                } catch (Exception e) {
                    log.error("计算相似度时发生错误", e);
                }
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
    private StringBuilder mergeCrossPageTable(StringBuilder tableContent, String tableFingerprint) {
        // 一般跨页表格处理逻辑
        String matchedFingerprint = null;
        CacheEntry matchedEntry = null;
        double maxSimilarity = 0;

        // 遍历缓存找到最相似的表格
        for (Map.Entry<String, CacheEntry> entry : crossPageTableCache.entrySet()) {
            String cachedFingerprint = entry.getKey();
            CacheEntry cacheEntry = entry.getValue();
            try {
                double similarity = calculateSimilarity(cachedFingerprint, tableFingerprint);
                if (similarity >= TABLE_SIMILARITY_THRESHOLD && similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    matchedFingerprint = cachedFingerprint;
                    matchedEntry = cacheEntry;
                }
            } catch (Exception e) {
                log.error("计算相似度时发生错误", e);
            }
        }

        StringBuilder mergedTable;
        if (matchedFingerprint != null) {
            mergedTable = new StringBuilder(matchedEntry.content);
            // 避免重复内容
            String content = tableContent.toString();
            if (!mergedTable.toString().contains(content)) {
                mergedTable.append(content);
                log.info("合并跨页表格: {}", matchedFingerprint);
            } else {
                log.info("跳过重复内容的跨页表格: {}", matchedFingerprint);
            }

            crossPageTableCache.put(matchedFingerprint, new CacheEntry(mergedTable));
            if (!matchedFingerprint.equals(tableFingerprint)) {
                crossPageTableCache.remove(tableFingerprint);
                log.info("剔除旧表格条目: {}", tableFingerprint);
            }
        } else {
            mergedTable = new StringBuilder(tableContent);
            CacheEntry newEntry = new CacheEntry(mergedTable);
            crossPageTableCache.put(tableFingerprint, newEntry);
        }
        return mergedTable;
    }

    /**
     * 合并入职申请表一和入职申请表二
     *
     * @param currentTable 当前表格内容
     * @param currentFormType 当前表格类型（入职申请表一或入职申请表二）
     * @param targetFormType 目标表格类型（入职申请表二或入职申请表一）
     * @return 合并后的表格内容
     */
    private StringBuilder mergeApplicationForms(StringBuilder currentTable, String currentFormType, String targetFormType) {
        // 查找目标表格
        CacheEntry targetEntry = null;
        String targetFingerprint = null;

        for (Map.Entry<String, CacheEntry> entry : crossPageTableCache.entrySet()) {
            if (entry.getKey().contains(targetFormType)) {
                targetEntry = entry.getValue();
                targetFingerprint = entry.getKey();
                break;
            }
        }

        if (targetEntry == null) {
            // 未找到目标表格，将当前表格放入缓存
            log.info("未找到{}，将{}放入缓存等待合并", targetFormType, currentFormType);
            return currentTable;
        }

        // 合并两个表格
        StringBuilder mergedTable = new StringBuilder();

        // 确保入职申请表一在前，入职申请表二在后
        if (currentFormType.equals("入职申请表一")) {
            mergedTable.append(currentTable);
            mergedTable.append(targetEntry.content);
        } else {
            mergedTable.append(targetEntry.content);
            mergedTable.append(currentTable);
        }

        log.info("成功合并{}和{}", "入职申请表一", "入职申请表二");

        // 更新缓存
        String newFingerprint = "入职申请表合并";
        crossPageTableCache.put(newFingerprint, new CacheEntry(mergedTable));

        // 移除旧的表格条目
        if (targetFingerprint != null) {
            crossPageTableCache.remove(targetFingerprint);
        }

        return mergedTable;
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
        cacheCleanupScheduler.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!cacheCleanupScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                cacheCleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            cacheCleanupScheduler.shutdownNow();
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

            // 将缓存中的表格内容添加到列表中
            for (Map.Entry<String, CacheEntry> entry : crossPageTableCache.entrySet()) {
                crossPageTables.add(entry.getValue().content);
                log.info("处理缓存表格: {}", entry.getKey());
            }

            // 清空缓存
            crossPageTableCache.clear();

            // 将跨页表格添加到批处理队列
            if (!crossPageTables.isEmpty()) {
                List<List<StringBuilder>> batchTables = new ArrayList<>();
                batchTables.add(crossPageTables);
                processBatchTables(batchTables);
            }
        }
    }
}
