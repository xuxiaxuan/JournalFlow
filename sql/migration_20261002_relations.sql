-- 为旧版数据库补充核心关系外键。请先执行 migration_20261001.sql 并备份数据库。
-- 若脚本因历史孤立数据失败，应先核对并修复对应数据，不要删除约束绕过问题。
USE journalflow;

ALTER TABLE user_role
  ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE RESTRICT;
ALTER TABLE category ADD CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id) ON DELETE RESTRICT;
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
ALTER TABLE llm_task
  ADD CONSTRAINT fk_llm_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_llm_version FOREIGN KEY (version_id) REFERENCES manuscript_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_llm_confirmer FOREIGN KEY (confirmed_by) REFERENCES user(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_llm_creator FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL;
ALTER TABLE operation_log ADD CONSTRAINT fk_operation_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL;
ALTER TABLE notification ADD CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE;
ALTER TABLE view_stat ADD CONSTRAINT fk_view_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscript(id) ON DELETE CASCADE;
