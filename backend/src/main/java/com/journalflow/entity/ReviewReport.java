package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_report")
public class ReviewReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long manuscriptId;
    private Long versionId;
    private Integer scoreNovelty;
    private Integer scoreQuality;
    private Integer scoreClarity;
    private String commentsToEditor;
    private String commentsToAuthor;
    /** ACCEPT/MINOR/MAJOR/REJECT */
    private String recommendation;
    private LocalDateTime submittedAt;
}
