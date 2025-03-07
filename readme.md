把生成的com文件，覆盖解压的aspose-word中，并重新打包

jar cvfm aspose-words-24.3-jdk17-crack.jar META-INF/MANIFEST.MF com/

# 待解决的问题清单
1.减少映射器调用次数。解析器按页分批次交付给映射处理器。收集一页的表格交给子线程映射处理。
初步方案：
（1）选择合适的线程池/虚拟线程，解析器收集一定数量的表格，提交给子线程去调用映射器。
（2）分页缓冲队列（容量50页，假设每页1个表格）配合超时批量提交策略。
（3）对象池复用Table实例，预处理元数据缓存

2.如何处理跨页分表格。
初步方案：
因为我们是按照页为单位进行映射处理的。因为该方式避免了大多数安全隐患。如大批量表格恶意攻击, 内存溢出风险，但是无法解决跨表数据的关联性问题
（1）结构验证：表头相似度>85%时合并（样式+列特征指纹）
（2）资源限制：单文档≤100页，每页≤10表，总单元格≤1000
（3）异常跳过：检测同类型表格连续重复超过5次，跳过该类型处理。

3.解析内容转义,避免出现特殊符号,导致字符串中断，形成执行语句。
（1）输入过滤：白名单正则过滤非常用符号
（2）双重转义：HTML实体化+CSS编码双保险
（3）输出防护：按SQL/HTML/JSON上下文动态编码
成功拦截：DROP TABLE类注入攻击，输出无害字符

4.测试横向表头的表格
