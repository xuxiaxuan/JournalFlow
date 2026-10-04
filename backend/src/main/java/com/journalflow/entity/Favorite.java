package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("favorite")
public class Favorite {
    private Long userId;
    private Long manuscriptId;
    private LocalDateTime createdAt;
}
