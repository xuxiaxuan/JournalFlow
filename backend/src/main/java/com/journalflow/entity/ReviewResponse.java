package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_response")
public class ReviewResponse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Long reportId;
    private Integer issueIndex;
    private String issueText;
    private String responseText;
    private String changeLocation;
    private Long versionId;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
}
