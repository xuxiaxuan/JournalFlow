package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("editorial_decision")
public class EditorialDecision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Long versionId;
    /** FORMAT/REVIEW/APPEAL/GOVERNANCE */
    private String stage;
    /** PASS/FAIL/ACCEPT/REVISE/REJECT/OVERTURN/DISMISSED/RETRACT */
    private String decision;
    private String reason;
    private String summaryReport;
    private Long llmTaskId;
    private Long decidedBy;
    private LocalDateTime decidedAt;
}
