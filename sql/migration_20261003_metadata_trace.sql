-- AI 元数据人工确认前后快照。执行前请备份数据库，并先执行前两份迁移。
USE journalflow;

ALTER TABLE llm_task
  ADD COLUMN retry_of_task_id BIGINT NULL COMMENT '失败重试来源任务' AFTER error_msg,
  ADD CONSTRAINT fk_llm_retry_source FOREIGN KEY (retry_of_task_id) REFERENCES llm_task(id) ON DELETE SET NULL;

CREATE TABLE metadata_revision (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id BIGINT NOT NULL,
  version_id    BIGINT NOT NULL,
  llm_task_id   BIGINT NOT NULL,
  changed_by    BIGINT NOT NULL,
  before_data   JSON NOT NULL COMMENT '写回前稿件元数据快照',
  after_data    JSON NOT NULL COMMENT '人工确认后的结构化结果',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_metadata_llm_task (llm_task_id),
  KEY idx_metadata_manuscript (manuscript_id, created_at),
  CONSTRAINT fk_metadata_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  CONSTRAINT fk_metadata_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  CONSTRAINT fk_metadata_llm_task FOREIGN KEY (llm_task_id) REFERENCES llm_task(id) ON DELETE RESTRICT,
  CONSTRAINT fk_metadata_editor FOREIGN KEY (changed_by) REFERENCES user(id) ON DELETE RESTRICT
) COMMENT='AI 元数据人工确认与写回修订记录';
