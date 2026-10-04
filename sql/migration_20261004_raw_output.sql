-- 保留模型原始文本，包括非法 JSON；不会删除现有任务。
USE journalflow;
ALTER TABLE llm_task MODIFY COLUMN raw_output LONGTEXT NULL COMMENT '模型原始输出，保留非法 JSON 与代码围栏';
