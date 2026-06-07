CREATE DATABASE IF NOT EXISTS campus_qa
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE campus_qa;

CREATE TABLE IF NOT EXISTS question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question VARCHAR(255) NOT NULL,
  answer TEXT NOT NULL,
  category VARCHAR(64) DEFAULT NULL,
  source VARCHAR(64) DEFAULT NULL,
  hit_count BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_pinned TINYINT NOT NULL DEFAULT 0,
  pinned_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_question (question),
  KEY idx_category (category),
  KEY idx_hit_count (hit_count),
  KEY idx_pinned_order (is_pinned, pinned_order)
);

CREATE TABLE IF NOT EXISTS question_embedding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  embedding_model VARCHAR(128) NOT NULL,
  vector_json LONGTEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_question_id (question_id),
  KEY idx_content_hash (content_hash)
);

CREATE TABLE IF NOT EXISTS contribution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question VARCHAR(255) NOT NULL,
  answer TEXT NOT NULL,
  category VARCHAR(64) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  submit_ip VARCHAR(64) NOT NULL,
  submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  audit_time DATETIME DEFAULT NULL,
  reject_reason VARCHAR(255) DEFAULT NULL,
  KEY idx_status_submit_time (status, submit_time),
  KEY idx_submit_ip_time (submit_ip, submit_time)
);

CREATE TABLE IF NOT EXISTS crawl_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_name VARCHAR(128) NOT NULL,
  target_url VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  total_found INT NOT NULL DEFAULT 0,
  total_inserted INT NOT NULL DEFAULT 0,
  start_time DATETIME DEFAULT NULL,
  end_time DATETIME DEFAULT NULL,
  error_message VARCHAR(500) DEFAULT NULL,
  KEY idx_status_start_time (status, start_time)
);

CREATE TABLE IF NOT EXISTS query_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  keyword VARCHAR(255) NOT NULL,
  matched_question_id BIGINT DEFAULT NULL,
  user_ip VARCHAR(64) NOT NULL,
  query_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_keyword (keyword),
  KEY idx_query_time (query_time),
  KEY idx_matched_question_id (matched_question_id),
  KEY idx_user_ip_time (user_ip, query_time)
);

CREATE TABLE IF NOT EXISTS sensitive_word (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  word VARCHAR(64) NOT NULL,
  level VARCHAR(16) NOT NULL DEFAULT 'high',
  enabled TINYINT NOT NULL DEFAULT 1,
  source VARCHAR(64) DEFAULT 'manual',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_word (word),
  KEY idx_enabled_level (enabled, level)
);
USE campus_qa;

CREATE TABLE IF NOT EXISTS question_embedding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  embedding_model VARCHAR(128) NOT NULL,
  vector_json LONGTEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_question_id (question_id),
  KEY idx_content_hash (content_hash)
);

USE campus_qa;

SELECT COUNT(*) AS total FROM question_embedding;

SELECT question_id, embedding_model, create_time
FROM question_embedding
ORDER BY id DESC
LIMIT 10;