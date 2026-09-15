-- RocketMQ 限量报名流水、业务死信与幂等索引；脚本可重复执行。
CREATE TABLE IF NOT EXISTS `tb_registration_request` (
  `registration_id` bigint(20) NOT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `registration_pass_id` bigint(20) UNSIGNED NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `message_id` varchar(128) DEFAULT NULL,
  `status` varchar(24) NOT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`registration_id`),
  UNIQUE KEY `uk_registration_request_user_pass` (`user_id`, `registration_pass_id`),
  UNIQUE KEY `uk_registration_request_event` (`event_id`),
  KEY `idx_registration_request_status_time` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_registration_dead_letter` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `registration_id` bigint(20) NOT NULL,
  `message_id` varchar(128) DEFAULT NULL,
  `reconsume_times` int NOT NULL DEFAULT 0,
  `failure_code` varchar(64) NOT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `payload` text NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_dead_letter_event` (`event_id`),
  KEY `idx_registration_dead_letter_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @has_user_pass_unique = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tb_activity_registration'
    AND INDEX_NAME IN ('uk_activity_registration_user_pass', 'uk_registration_user_pass')
    AND NON_UNIQUE = 0
);
SET @add_user_pass_unique = IF(
  @has_user_pass_unique = 0,
  'ALTER TABLE tb_activity_registration ADD UNIQUE KEY uk_activity_registration_user_pass (user_id, registration_pass_id)',
  'SELECT 1'
);
PREPARE statement FROM @add_user_pass_unique;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_pass_time_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tb_activity_registration'
    AND INDEX_NAME = 'idx_activity_registration_pass_time'
);
SET @add_pass_time_index = IF(
  @has_pass_time_index = 0,
  'ALTER TABLE tb_activity_registration ADD KEY idx_activity_registration_pass_time (registration_pass_id, create_time)',
  'SELECT 1'
);
PREPARE statement FROM @add_pass_time_index;
EXECUTE statement;
DEALLOCATE PREPARE statement;
