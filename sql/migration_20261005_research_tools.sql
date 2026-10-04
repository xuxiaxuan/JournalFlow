USE journalflow;

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
