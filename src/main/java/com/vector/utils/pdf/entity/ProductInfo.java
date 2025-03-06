package com.vector.utils.pdf.entity;

import com.vector.utils.pdf.annotation.TableFieldMap;
import lombok.Data;

import java.io.Serializable;
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
     * 单位
     */
    @TableFieldMap("单位")
    private String unit;

    /**
     * 信息名称
     */
    @TableFieldMap("信息名称")
    private String title;
    /**
     * 时间
     */
    @TableFieldMap("时间")
    private Date pubDate;
    /**
     * 密级
     */
    @TableFieldMap("密级")
    private String classified;

    /**
     * 信息主题
     */
    @TableFieldMap("信息主题")
    private String theme;

    /**
     * 信息种类 视频 音频 图片 实物 其它
     */
    @TableFieldMap("信息种类")
    private String category;

    /**
     * 制品规格
     */
    @TableFieldMap("制品规格")
    private String scales;

    /**
     * 使用语种
     */
    @TableFieldMap("使用语种")
    private String lang;

    /**
     * 使用方向 东部战略方向 南部 西部 北部 其他
     */
    @TableFieldMap("使用方向")
    private String direction;

    /**
     * 作战层级-多选 战略 战役 战术
     */
    @TableFieldMap("作战层级")
    private String operLevel;

    /**
     * 作战阶段
     */
    @TableFieldMap("作战阶段")
    private String stages;

    /**
     * 使用场景
     */
    @TableFieldMap("使用场景")
    private String scenes;

    /**
     * 作战目的
     */
    @TableFieldMap("作战目的")
    private String aim;

    /**
     * 目标对象及其心理特点
     */
    @TableFieldMap("目标对象及其心理特点")
    private String objectPsychology;

    /**
     * 谋略战法
     */
    @TableFieldMap("谋略战法")
    private String militaryStrategy;

    /**
     * 投送平台
     */
    @TableFieldMap("投送平台")
    private String deliveryPlatform;

    /**
     * 投送身份
     */
    @TableFieldMap("投送身份")
    private String sendingIdentity;

    /**
     * 预期效果
     */
    @TableFieldMap("预期效果")
    private String intendedEffect;

    /**
     * 风险评估
     */
    @TableFieldMap("风险评估")
    private String riskAssessment;

    /**
     * 创新内容
     */
    @TableFieldMap("创新内容")
    private String innovativeContent;

    /**
//     * 信息内容
     */
    @TableFieldMap("信息内容")
    private String content;

    /**
     * 创建时间
     */
    private Date createAt;

    /**
     * 修改时间
     */
    private Date updateAt;

    /**
     * 创建人id
     */
    private Long createBy;

    /**
     * 修改人id
     */
    private Long updateBy;
}
