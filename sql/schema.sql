-- =====================================================================
-- 基于大模型辅助的学术期刊投稿审稿与出版流程系统 —— 数据库 Schema
-- MySQL 8.0 / utf8mb4 / InnoDB
-- =====================================================================
USE journalflow;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS review_response, user, role, user_role, category, tag, manuscript_tag,
  manuscript, manuscript_version, manuscript_author,
  review_assignment, review_report, editorial_decision, revision,
  comment, favorite, subscription, follow,
  report, appeal, copyright_record, metadata_revision,
  llm_task, operation_log, notification, view_stat;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 用户与权限 ----------------
CREATE TABLE user (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  username        VARCHAR(50)  NOT NULL,
  password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  real_name       VARCHAR(50)  NOT NULL,
  email           VARCHAR(100) NULL,
  affiliation     VARCHAR(200) NULL COMMENT '单位（虚构演示数据）',
  research_fields VARCHAR(300) NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) COMMENT='用户（最小必要：不收集手机号/证件号）';

CREATE TABLE role (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(20) NOT NULL,
  name VARCHAR(50) NOT NULL,
  UNIQUE KEY uk_role_code (code)
) COMMENT='角色（作者/审稿专家/编辑/管理员）';

CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) COMMENT='用户-角色多对多（允许兼任）';

-- ---------------- 稿件主链 ----------------
CREATE TABLE category (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  name      VARCHAR(100) NOT NULL,
  parent_id BIGINT NULL,
  sort_no   INT NOT NULL DEFAULT 0
) COMMENT='学科分类树';

CREATE TABLE manuscript (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  submitter_id      BIGINT      NOT NULL,
  title             VARCHAR(200) NOT NULL,
  abstract          TEXT        NULL,
  keywords          VARCHAR(500) NULL,
  category_id       BIGINT      NULL,
  status            VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
    COMMENT 'DRAFT/SUBMITTED/IN_FORMAT_REVIEW/UNDER_REVIEW/PENDING_DECISION/ACCEPTED/IN_PRODUCTION/PUBLISHED/REVISION_REQUIRED/REJECTED/WITHDRAWN',
  current_version_id BIGINT     NULL,
  volume            VARCHAR(20)  NULL COMMENT '卷',
  issue             VARCHAR(20)  NULL COMMENT '期',
  pages             VARCHAR(30)  NULL COMMENT '页码',
  doi               VARCHAR(100) NULL COMMENT 'DOI 位',
  published_at      DATETIME    NULL,
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status),
  KEY idx_submitter (submitter_id),
  KEY idx_category (category_id),
  KEY idx_published_at (published_at),
  UNIQUE KEY uk_doi (doi),
  FULLTEXT KEY ft_title_abstract (title, abstract) WITH PARSER ngram
) COMMENT='稿件主表';

CREATE TABLE manuscript_version (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id BIGINT      NOT NULL,
  version_no    INT         NOT NULL,
  title         VARCHAR(200) NOT NULL,
  abstract      TEXT        NULL,
  keywords      VARCHAR(500) NULL,
  file_path     VARCHAR(500) NOT NULL,
  file_name     VARCHAR(300) NOT NULL COMMENT '原始文件名',
  file_size     BIGINT      NOT NULL DEFAULT 0,
  file_hash     CHAR(64)    NOT NULL COMMENT 'SHA-256',
  change_note   VARCHAR(500) NULL COMMENT '相对上一版的修改说明',
  created_by    BIGINT      NOT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ms_ver (manuscript_id, version_no)
) COMMENT='稿件版本链，历史版本只读';

CREATE TABLE manuscript_author (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id    BIGINT      NOT NULL,
  user_id          BIGINT      NULL COMMENT '已注册作者',
  author_name      VARCHAR(100) NOT NULL COMMENT '姓名快照（含未注册合作者）',
  affiliation      VARCHAR(200) NULL,
  author_order     INT         NOT NULL DEFAULT 1,
  is_corresponding TINYINT     NOT NULL DEFAULT 0 COMMENT '通讯作者',
  KEY idx_ms (manuscript_id)
) COMMENT='稿件作者列表';

CREATE TABLE tag (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  UNIQUE KEY uk_tag (name)
) COMMENT='标签';

CREATE TABLE manuscript_tag (
  manuscript_id BIGINT NOT NULL,
  tag_id        BIGINT NOT NULL,
  PRIMARY KEY (manuscript_id, tag_id)
) COMMENT='稿件-标签';

-- ---------------- 审稿流程 ----------------
CREATE TABLE review_assignment (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id BIGINT NOT NULL,
  version_id    BIGINT NOT NULL COMMENT '审的是哪一版',
  reviewer_id   BIGINT NOT NULL,
  invited_by    BIGINT NOT NULL COMMENT '邀请编辑',
  invited_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  due_at        DATETIME NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING待接受/ACCEPTED已接受/DECLINED已拒绝/COMPLETED已完成/EXPIRED已超时',
  UNIQUE KEY uk_ms_ver_reviewer (manuscript_id, version_id, reviewer_id)
) COMMENT='审稿邀请与任务';

CREATE TABLE review_report (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  assignment_id      BIGINT NOT NULL,
  manuscript_id      BIGINT NOT NULL,
  version_id         BIGINT NOT NULL,
  score_novelty      INT NOT NULL COMMENT '创新性 1-10',
  score_quality      INT NOT NULL COMMENT '学术质量 1-10',
  score_clarity      INT NOT NULL COMMENT '表达清晰度 1-10',
  comments_to_editor TEXT NULL,
  comments_to_author TEXT NOT NULL,
  recommendation     VARCHAR(20) NOT NULL COMMENT 'ACCEPT录用/MINOR小修/MAJOR大修/REJECT退稿',
  submitted_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_assignment (assignment_id)
) COMMENT='结构化审稿意见';

CREATE TABLE editorial_decision (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id BIGINT NOT NULL,
  version_id    BIGINT NOT NULL,
  stage         VARCHAR(20) NOT NULL COMMENT 'FORMAT形式审查/REVIEW复审/APPEAL申诉终审/GOVERNANCE举报处置/PUBLICATION出版归档',
  decision      VARCHAR(20) NOT NULL COMMENT 'PASS/FAIL/ACCEPT/REVISE/REJECT/OVERTURN/DISMISSED/RETRACT/PUBLISHED',
  reason        TEXT NULL,
  summary_report TEXT NULL COMMENT '审稿综合意见（可由 LLM 汇总+人工确认产生）',
  llm_task_id   BIGINT NULL COMMENT '关联的 LLM 汇总任务',
  decided_by    BIGINT NOT NULL,
  decided_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='编辑决定记录';

CREATE TABLE revision (
  id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id         BIGINT NOT NULL,
  based_version_id      BIGINT NOT NULL COMMENT '基于哪一版修改',
  new_version_id        BIGINT NOT NULL COMMENT '修回产生的新版本',
  response_to_reviewers TEXT NULL COMMENT '修改说明/答复审稿人',
  submitted_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='修回记录';

-- ---------------- 互动与传播 ----------------
CREATE TABLE comment (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_type VARCHAR(20) NOT NULL COMMENT 'MANUSCRIPT稿件/COMMENT评论',
  target_id   BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  content     TEXT NOT NULL,
  parent_id   BIGINT NULL COMMENT '两级回复',
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0隐藏 2删除',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_target (target_type, target_id)
) COMMENT='评论（已出版文章，支持回复，软删除）';

CREATE TABLE favorite (
  user_id       BIGINT NOT NULL,
  manuscript_id BIGINT NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, manuscript_id)
) COMMENT='收藏';

CREATE TABLE subscription (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  category_id BIGINT NULL COMMENT '订阅学科',
  keyword     VARCHAR(100) NULL COMMENT '订阅关键词',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) COMMENT='订阅（新文章发布时推送站内消息）';

CREATE TABLE follow (
  follower_id BIGINT NOT NULL,
  followee_id BIGINT NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, followee_id)
) COMMENT='关注作者';

-- ---------------- 治理 ----------------
CREATE TABLE report (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id BIGINT NOT NULL,
  target_type VARCHAR(20) NOT NULL COMMENT 'MANUSCRIPT/COMMENT/USER',
  target_id   BIGINT NOT NULL,
  reason_type VARCHAR(30) NOT NULL COMMENT '抄袭/造假/侵权/不当言论/其他',
  description TEXT NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处理/PROCESSING处理中/RESOLVED已处置/DISMISSED驳回',
  handled_by  BIGINT NULL,
  handled_at  DATETIME NULL,
  resolution  TEXT NULL,
  target_status_before VARCHAR(30) NULL COMMENT '实际处置前状态，申诉成立时按此恢复',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_status (status)
) COMMENT='举报工单';

CREATE TABLE appeal (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id    BIGINT NULL COMMENT '举报处置申诉',
  decision_id  BIGINT NULL COMMENT '退稿/处置决定申诉',
  appellant_id BIGINT NOT NULL,
  content      TEXT NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED已裁决/DISMISSED驳回',
  result       TEXT NULL,
  handled_by   BIGINT NULL,
  handled_at   DATETIME NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='申诉（退稿申诉1次 + 举报处置申诉）';

CREATE TABLE copyright_record (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id  BIGINT NOT NULL,
  version_id     BIGINT NOT NULL,
  license_type   VARCHAR(50) NOT NULL DEFAULT 'CC-BY-4.0',
  agreement_text TEXT NOT NULL,
  agreement_hash CHAR(64) NULL COMMENT '协议文本 SHA-256 留痕',
  signed_by      BIGINT NOT NULL COMMENT '签署的作者',
  signed_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_copyright_version (manuscript_id, version_id)
) COMMENT='版权协议签署留痕';

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
  KEY idx_metadata_manuscript (manuscript_id, created_at)
) COMMENT='AI 元数据人工确认与写回修订记录';

-- ---------------- 智能化与工程 ----------------
CREATE TABLE llm_task (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  scene            VARCHAR(20) NOT NULL COMMENT 'EXTRACT抽取/SUMMARY汇总/EXPLAIN解释',
  manuscript_id    BIGINT NOT NULL,
  version_id       BIGINT NOT NULL,
  template_id      VARCHAR(30) NOT NULL COMMENT '输入模板编号 TPL-EXTRACT-01 等',
  input_digest     VARCHAR(200) NULL COMMENT '输入内容摘要',
  provider         VARCHAR(30) NOT NULL,
  model            VARCHAR(60) NOT NULL,
  raw_output       LONGTEXT NULL COMMENT '模型原始输出，保留非法 JSON 与代码围栏',
  parsed_output    JSON NULL COMMENT 'Schema 校验后的结构化输出',
  confirmed_output JSON NULL COMMENT '人工确认/修订后的最终输出',
  status           VARCHAR(15) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED/CONFIRMED',
  confirmed_by     BIGINT NULL,
  confirmed_at     DATETIME NULL,
  latency_ms       INT NULL,
  error_msg        VARCHAR(500) NULL,
  retry_of_task_id BIGINT NULL COMMENT '失败重试来源任务',
  created_by       BIGINT NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_scene_status (scene, status),
  KEY idx_ms (manuscript_id)
) COMMENT='大模型调用与人工确认留痕（AI 可溯源锚点）';

CREATE TABLE operation_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NULL,
  action      VARCHAR(50) NOT NULL,
  object_type VARCHAR(30) NULL,
  object_id   BIGINT NULL,
  detail      JSON NULL,
  ip          VARCHAR(45) NULL,
  success     TINYINT NOT NULL DEFAULT 1,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_action (action),
  KEY idx_user (user_id),
  KEY idx_created (created_at)
) COMMENT='操作日志（AOP 统一写入）';

CREATE TABLE notification (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  type       VARCHAR(30) NOT NULL COMMENT 'REVIEW_INVITE/DECISION/REVISE_REQUEST/PUBLISH/SUBSCRIPTION/REPORT/APPEAL/LLM/SYSTEM',
  title      VARCHAR(200) NOT NULL,
  content    TEXT NULL,
  ref_id     BIGINT NULL,
  read_at    DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_read (user_id, read_at)
) COMMENT='站内消息';

CREATE TABLE view_stat (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id  BIGINT NOT NULL,
  view_count     INT NOT NULL DEFAULT 0,
  download_count INT NOT NULL DEFAULT 0,
  stat_date      DATE NOT NULL,
  UNIQUE KEY uk_ms_date (manuscript_id, stat_date)
) COMMENT='阅读/下载统计（推荐与运营分析数据源）';

-- ---------------- 核心对象关系约束 ----------------
-- 多态引用（comment.target_id、report.target_id、notification.ref_id、operation_log.object_id）
-- 由业务层按类型校验，其余稳定关系使用数据库外键保证一致性。
ALTER TABLE user_role
  ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE RESTRICT;
ALTER TABLE category
  ADD CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id) ON DELETE RESTRICT;
ALTER TABLE manuscript
  ADD CONSTRAINT fk_manuscript_submitter FOREIGN KEY (submitter_id) REFERENCES user(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_manuscript_category FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_manuscript_current_version FOREIGN KEY (current_version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT;
ALTER TABLE manuscript_version
  ADD CONSTRAINT fk_version_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_version_creator FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE RESTRICT;
ALTER TABLE manuscript_author
  ADD CONSTRAINT fk_author_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_author_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL;
ALTER TABLE manuscript_tag
  ADD CONSTRAINT fk_manuscript_tag_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_manuscript_tag_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT;
ALTER TABLE review_assignment
  ADD CONSTRAINT fk_assignment_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_assignment_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_assignment_reviewer FOREIGN KEY (reviewer_id) REFERENCES user(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_assignment_inviter FOREIGN KEY (invited_by) REFERENCES user(id) ON DELETE RESTRICT;
ALTER TABLE review_report
  ADD CONSTRAINT fk_review_report_assignment FOREIGN KEY (assignment_id) REFERENCES review_assignment(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_review_report_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_review_report_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT;
ALTER TABLE editorial_decision
  ADD CONSTRAINT fk_decision_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_decision_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_decision_llm_task FOREIGN KEY (llm_task_id) REFERENCES llm_task(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_decision_user FOREIGN KEY (decided_by) REFERENCES user(id) ON DELETE RESTRICT;
ALTER TABLE revision
  ADD CONSTRAINT fk_revision_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_revision_based_version FOREIGN KEY (based_version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_revision_new_version FOREIGN KEY (new_version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT;
ALTER TABLE comment
  ADD CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment(id) ON DELETE RESTRICT;
ALTER TABLE favorite
  ADD CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_favorite_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE CASCADE;
ALTER TABLE subscription
  ADD CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_subscription_category FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE RESTRICT;
ALTER TABLE follow
  ADD CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES user(id) ON DELETE CASCADE;
ALTER TABLE report
  ADD CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES user(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_report_handler FOREIGN KEY (handled_by) REFERENCES user(id) ON DELETE SET NULL;
ALTER TABLE appeal
  ADD CONSTRAINT fk_appeal_report FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_appeal_decision FOREIGN KEY (decision_id) REFERENCES editorial_decision(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_appeal_appellant FOREIGN KEY (appellant_id) REFERENCES user(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_appeal_handler FOREIGN KEY (handled_by) REFERENCES user(id) ON DELETE SET NULL;
ALTER TABLE copyright_record
  ADD CONSTRAINT fk_copyright_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_copyright_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_copyright_signer FOREIGN KEY (signed_by) REFERENCES user(id) ON DELETE RESTRICT;
ALTER TABLE metadata_revision
  ADD CONSTRAINT fk_metadata_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_metadata_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_metadata_llm_task FOREIGN KEY (llm_task_id) REFERENCES llm_task(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_metadata_editor FOREIGN KEY (changed_by) REFERENCES user(id) ON DELETE RESTRICT;
ALTER TABLE llm_task
  ADD CONSTRAINT fk_llm_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_llm_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_llm_confirmer FOREIGN KEY (confirmed_by) REFERENCES user(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_llm_retry_source FOREIGN KEY (retry_of_task_id) REFERENCES llm_task(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_llm_creator FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL;
ALTER TABLE operation_log
  ADD CONSTRAINT fk_operation_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL;
ALTER TABLE notification
  ADD CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE;
ALTER TABLE view_stat
  ADD CONSTRAINT fk_view_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE CASCADE;

-- Auxiliary responses are append-only. Existing workflow tables and states are unchanged.
CREATE TABLE IF NOT EXISTS review_response (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  manuscript_id BIGINT NOT NULL,
  report_id BIGINT NOT NULL,
  issue_index INT NOT NULL,
  issue_text TEXT NOT NULL,
  response_text TEXT NOT NULL,
  change_location VARCHAR(1000) NOT NULL DEFAULT '',
  version_id BIGINT NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_response_issue (manuscript_id, report_id, issue_index, id),
  CONSTRAINT fk_response_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  CONSTRAINT fk_response_report FOREIGN KEY (report_id) REFERENCES review_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_response_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  CONSTRAINT fk_response_user FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
