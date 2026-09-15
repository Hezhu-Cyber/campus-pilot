-- 智能客服转人工工单表。
CREATE TABLE IF NOT EXISTS tb_support_ticket (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单主键',
  user_id BIGINT NOT NULL COMMENT '提交用户',
  thread_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '助手会话标识',
  category VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT '问题分类',
  subject VARCHAR(128) NOT NULL COMMENT '工单标题',
  content VARCHAR(2000) NOT NULL COMMENT '问题详情',
  handler_id BIGINT NULL COMMENT '最后处理的管理员',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/RESOLVED/CLOSED',
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_support_ticket_user_time (user_id, create_time),
  KEY idx_support_ticket_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能客服工单';
