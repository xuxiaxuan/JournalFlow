package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 元数据确认写回的前后快照。 */
@Data
@TableName("metadata_revision")
public class MetadataRevision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Long versionId;
    private Long llmTaskId;
    private Long changedBy;
    private String beforeData;
    private String afterData;
    private LocalDateTime createdAt;
}
