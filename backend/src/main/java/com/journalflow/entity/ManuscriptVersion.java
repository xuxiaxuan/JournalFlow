package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "manuscript_version", autoResultMap = true)
public class ManuscriptVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Integer versionNo;
    private String title;
    @TableField("abstract")
    private String abstract_;
    private String keywords;
    private String filePath;
    private String fileName;
    private Long fileSize;
    private String fileHash;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdAt;
}
