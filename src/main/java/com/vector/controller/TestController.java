package com.vector.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vector.utils.pdf.PdfTableExtractor;

/**
 * @author YuanJie
 * @ClassName TestController
 * @description: TODO
 * @date 2025/3/5 11:34
 */
@RequestMapping("/test")
@RestController
@RequiredArgsConstructor
public class TestController {

    @RequestMapping("/hello")
    public String hello(){
        String path = "C:\\Users\\YuanJie\\Desktop\\demo-aspose-pdf-table-read\\入职申请表.pdf";
//        path = "C:\\Users\\YuanJie\\Desktop\\demo-aspose-pdf-table-read\\横向表头.pdf";
        PdfTableExtractor.tableAnalyze(path);
        return "hello";
    }
}
