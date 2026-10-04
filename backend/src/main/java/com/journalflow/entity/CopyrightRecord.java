package com.journalflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("copyright_record")
public class CopyrightRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long manuscriptId;
    private Long versionId;
    private String licenseType;
    private String agreementText;
    private String agreementHash;
    private Long signedBy;
    private LocalDateTime signedAt;
}
