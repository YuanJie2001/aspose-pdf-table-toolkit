package com.vector.utils.pdf.entity;

import com.aspose.slides.internal.oe.age;
import com.vector.utils.pdf.annotation.TableFieldMap;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 
 * @TableName ujcms_product_info
 */
@Data
public class ProductInfo implements Serializable {
    /**
     * 
     */
    private Long id;
    /**
     * 编号
     */
    private Long numId;
    /**
     * 资源id
     */
    private Long resourceId;

    /**
     * 原文件id
     */
    private Long fileId;
    /**
     * 姓名
     */
    @TableFieldMap("姓名")
    private String name;

    /**
     * 时间
     */
    @TableFieldMap("时间")
    private Date pubDate;
    /**
     * 爱好
     */
    @TableFieldMap("爱好")
    private String hobby;
    /**
     * 年龄
     */
    @TableFieldMap("年龄")
    private Byte age;

    /**
     * 特长
     */
    @TableFieldMap("特长")
    private String theme;

    /**
     * 专业技能
     */
    @TableFieldMap("专业技能")
    private String skill;

    /**
     * 项目经历
     */
    @TableFieldMap("项目经历")
    private String exp;

    /**
     * 语言
     */
    @TableFieldMap("语言")
    private String lang;

    /**
     * 专业方向
     */
    @TableFieldMap("专业方向")
    private String major;

    /**
     * 投递方向
     */
    @TableFieldMap("投递方向")
    private String sender;

    /**
     * 投递原因
     */
    @TableFieldMap("投递原因")
    private String sendCase;

    /**
     * 个人爱好
     */
    @TableFieldMap("个人爱好")
    private String priHobby;

    /**
     * 出生日期
     */
    @TableFieldMap("出生日期")
    private String birthDate;

    /**
     * 毕业学校
     */
    @TableFieldMap("毕业学校")
    private String university;

    /**
     * 期望年薪
     */
    @TableFieldMap("期望年薪")
    private String salary;

    /**
     * 性别
     */
    @TableFieldMap("性别")
    private String gender;

    /**
     * 联系方式
     */
    @TableFieldMap("联系方式")
    private String contact;

    /**
     * 预期效果
     */
    @TableFieldMap("预期效果")
    private String intendedEffect;

    /**
     * 个人评价
     */
    @TableFieldMap("个人评价")
    private String selfEvaluation;

    /**
     * 自我批评
     */
    @TableFieldMap("自我批评")
    private String selfCriticism;

    /**
//     * 信息内容
     */
    @TableFieldMap("信息内容")
    private String content;

    /**
     * 创建时间
     */
    private LocalDate createAt;

    /**
     * 修改时间
     */
    private LocalDate updateAt;

    /**
     * 创建人id
     */
    private Long createBy;

    /**
     * 修改人id
     */
    private Long updateBy;
}
