package com.vector.utils.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.awt.geom.Rectangle2D;
import java.io.*;

/**
 * @author YuanJie
 * @ClassName PDFboxTable
 * @description: TODO
 * @date 2025/2/28 15:34
 */
@Deprecated
public class PDFboxTable {
    private static final String path = "C:\\Users\\YuanJie\\Desktop\\aspose-pdf-table-toolkit\\入职申请表.pdf";

    public static void main(String[] args) throws IOException {
        //加载PDF文件
        PDDocument document = Loader.loadPDF(new FileInputStream(path).readAllBytes());

        //按区域读取文本剥离器
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        //新建区域，坐标：x,y；宽高：width，height
        Rectangle2D rectangle2D = new Rectangle2D.Double( 20, 20, 1080,600);

        //设置区域
        stripper.addRegion("regionName",rectangle2D);
        //按位置进行排序
        stripper.setSortByPosition(true);
        //获取页码树
        PDPageTree tree  = document.getPages();
        //获取指定页，从0开始
        PDPage page = tree.get(0);
        //提取页面信息
        stripper.extractRegions(page);
        //获取指定区域名称对应区域的文本
        String regionText = stripper.getTextForRegion("regionName");
        System.out.println(regionText);
        //关闭文档对象
        document.close();

    }
}
