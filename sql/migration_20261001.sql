-- 适用于已按旧版 schema.sql 初始化的数据库；全新环境无需执行。
-- 执行前建议备份数据库。若 DOI 存在重复值，唯一索引创建会失败，请先处理重复数据。

USE journalflow;

ALTER TABLE report
  ADD COLUMN target_status_before VARCHAR(30) NULL
    COMMENT '实际处置前状态，申诉成立时按此恢复'
    AFTER resolution;

ALTER TABLE manuscript
  ADD UNIQUE KEY uk_doi (doi);
