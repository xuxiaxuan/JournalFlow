package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("revision")
public class Revision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Long basedVersionId;
    private Long newVersionId;
    private String responseToReviewers;
    private LocalDateTime submittedAt;
}
