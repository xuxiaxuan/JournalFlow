package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_assignment")
public class ReviewAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Long versionId;
    private Long reviewerId;
    private Long invitedBy;
    private LocalDateTime invitedAt;
    private LocalDateTime dueAt;
    /** PENDING/ACCEPTED/DECLINED/COMPLETED/EXPIRED */
    private String status;
}
