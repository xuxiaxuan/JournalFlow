package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_task")
public class LlmTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** EXTRACT/SUMMARY/EXPLAIN */
    private String scene;
    private Long manuscriptId;
    private Long versionId;
    private String templateId;
    private String inputDigest;
    private String provider;
    private String model;
    private String rawOutput;
    private String parsedOutput;
    private String confirmedOutput;
    /** RUNNING/SUCCESS/FAILED/CONFIRMED */
    private String status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Integer latencyMs;
    private String errorMsg;
    private Long retryOfTaskId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
