package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    /** MANUSCRIPT/COMMENT/USER */
    private String targetType;
    private Long targetId;
    private String reasonType;
    private String description;
    /** PENDING/PROCESSING/RESOLVED/DISMISSED */
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String resolution;
    /** 实际处置前的对象状态，用于申诉成立后精确恢复。 */
    private String targetStatusBefore;
    private LocalDateTime createdAt;
}
