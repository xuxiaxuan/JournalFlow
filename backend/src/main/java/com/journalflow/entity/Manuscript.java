package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "manuscript", autoResultMap = true)
public class Manuscript {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submitterId;
    private String title;
    @TableField("abstract")
    private String abstract_;
    private String keywords;
    private Long categoryId;
    /** @see com.journalflow.statemachine.ManuscriptStatus */
    private String status;
    private Long currentVersionId;
    private String volume;
    private String issue;
    private String pages;
    private String doi;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
