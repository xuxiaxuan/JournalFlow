package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appeal")
public class Appeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long decisionId;
    private Long appellantId;
    private String content;
    /** PENDING/RESOLVED/DISMISSED */
    private String status;
    private String result;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
