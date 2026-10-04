package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("manuscript_tag")
public class ManuscriptTag {
    private Long manuscriptId;
    private Long tagId;
}
