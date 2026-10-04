package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("view_stat")
public class ViewStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Integer viewCount;
    private Integer downloadCount;
    private LocalDate statDate;
}
