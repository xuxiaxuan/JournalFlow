package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("manuscript_author")
public class ManuscriptAuthor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    /** 已注册作者；未注册合作者为 null，用 authorName 快照 */
    private Long userId;
    private String authorName;
    private String affiliation;
    private Integer authorOrder;
    private Integer isCorresponding;
}
