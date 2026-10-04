package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** MANUSCRIPT/COMMENT */
    private String targetType;
    private Long targetId;
    private Long userId;
    private String content;
    private Long parentId;
    /** 1正常 0隐藏 2删除 */
    private Integer status;
    private LocalDateTime createdAt;
}
