-- CampusPilot 校园活动平台初始化脚本
-- 面向全新数据库；不依赖任何旧项目表或二次改名迁移。
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `tb_registration_dead_letter`;
DROP TABLE IF EXISTS `tb_registration_request`;
DROP TABLE IF EXISTS `tb_activity_registration`;
DROP TABLE IF EXISTS `tb_limited_registration_quota`;
DROP TABLE IF EXISTS `tb_registration_pass`;
DROP TABLE IF EXISTS `tb_campus_post_comment`;
DROP TABLE IF EXISTS `tb_campus_post`;
DROP TABLE IF EXISTS `tb_user_info`;
DROP TABLE IF EXISTS `tb_activity`;
DROP TABLE IF EXISTS `tb_activity_category`;
DROP TABLE IF EXISTS `tb_user`;

CREATE TABLE `tb_user` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `phone` varchar(11) NOT NULL,
  `password` varchar(128) NOT NULL DEFAULT '',
  `nick_name` varchar(32) NOT NULL,
  `icon` varchar(255) NOT NULL DEFAULT '',
  `role` varchar(20) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ORGANIZER/ADMIN',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户';

CREATE TABLE `tb_user_info` (
  `user_id` bigint UNSIGNED NOT NULL,
  `city` varchar(64) NOT NULL DEFAULT '',
  `introduce` varchar(128) DEFAULT NULL,
  `gender` tinyint(1) DEFAULT NULL,
  `birthday` date DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户校园资料';

CREATE TABLE `tb_activity_category` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `icon` varchar(255) NOT NULL,
  `sort` int UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园活动分类';

CREATE TABLE `tb_activity` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `organizer_id` bigint UNSIGNED NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` text,
  `type_id` bigint UNSIGNED NOT NULL,
  `images` varchar(1024) NOT NULL,
  `area` varchar(128) DEFAULT NULL,
  `address` varchar(255) NOT NULL,
  `avg_price` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '报名费用，单位为元',
  `sold` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '已报名人数',
  `open_hours` varchar(64) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `registration_deadline` datetime DEFAULT NULL,
  `capacity` int UNSIGNED DEFAULT NULL,
  `activity_status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_category` (`type_id`),
  KEY `idx_activity_organizer` (`organizer_id`),
  KEY `idx_activity_time_status` (`start_time`, `activity_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园活动';

CREATE TABLE `tb_campus_post` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `activity_id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `title` varchar(255) NOT NULL,
  `images` varchar(2048) NOT NULL,
  `content` text NOT NULL,
  `liked` int UNSIGNED NOT NULL DEFAULT 0,
  `comments` int UNSIGNED NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_activity_time` (`activity_id`, `create_time`),
  KEY `idx_post_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园活动动态';

CREATE TABLE `tb_campus_post_comment` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED NOT NULL,
  `post_id` bigint UNSIGNED NOT NULL,
  `parent_id` bigint UNSIGNED DEFAULT NULL,
  `answer_id` bigint UNSIGNED DEFAULT NULL,
  `content` varchar(500) NOT NULL,
  `liked` int UNSIGNED NOT NULL DEFAULT 0,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comment_post_time` (`post_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园动态讨论';

CREATE TABLE `tb_registration_pass` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `activity_id` bigint UNSIGNED NOT NULL,
  `title` varchar(255) NOT NULL,
  `sub_title` varchar(255) DEFAULT NULL,
  `rules` varchar(1024) DEFAULT NULL,
  `pay_value` bigint UNSIGNED NOT NULL DEFAULT 0,
  `actual_value` bigint UNSIGNED NOT NULL DEFAULT 0,
  `type` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '0普通报名，1限量报名',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_registration_pass_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名方案';

CREATE TABLE `tb_limited_registration_quota` (
  `registration_pass_id` bigint UNSIGNED NOT NULL,
  `stock` int UNSIGNED NOT NULL,
  `begin_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`registration_pass_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限量活动剩余名额';

CREATE TABLE `tb_activity_registration` (
  `id` bigint NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `registration_pass_id` bigint UNSIGNED NOT NULL,
  `pay_type` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `status` tinyint UNSIGNED NOT NULL DEFAULT 1 COMMENT '1已报名，4已取消',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time` timestamp NULL DEFAULT NULL,
  `use_time` timestamp NULL DEFAULT NULL,
  `refund_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_user_pass` (`user_id`, `registration_pass_id`),
  KEY `idx_registration_pass_time` (`registration_pass_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名记录';

CREATE TABLE `tb_registration_request` (
  `registration_id` bigint NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `registration_pass_id` bigint UNSIGNED NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `message_id` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_reason` varchar(512) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`registration_id`),
  UNIQUE KEY `uk_registration_request_event` (`event_id`),
  UNIQUE KEY `uk_registration_request_user_pass` (`user_id`, `registration_pass_id`),
  KEY `idx_registration_request_status_time` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步报名处理流水';

CREATE TABLE `tb_registration_dead_letter` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `registration_id` bigint NOT NULL,
  `message_id` varchar(128) DEFAULT NULL,
  `reconsume_times` int NOT NULL DEFAULT 0,
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_reason` varchar(512) DEFAULT NULL,
  `payload` text,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_dead_letter_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名异常待处理记录';

INSERT INTO `tb_user` (`id`, `phone`, `nick_name`, `icon`, `role`) VALUES
  (1, '13800000001', '校园管理员', '/imgs/icons/default-icon.png', 'ADMIN'),
  (2, '13800000002', '学生会活动中心', '/imgs/icons/default-icon.png', 'ORGANIZER'),
  (3, '13800000003', '校园同学', '/imgs/icons/default-icon.png', 'STUDENT');

INSERT INTO `tb_activity_category` (`id`, `name`, `icon`, `sort`) VALUES
  (1, '学术讲座', 'types-campus/lecture.svg', 1),
  (2, '体育赛事', 'types-campus/sports.svg', 2),
  (3, '社团活动', 'types-campus/club.svg', 3),
  (4, '文艺演出', 'types-campus/arts.svg', 4),
  (5, '志愿服务', 'types-campus/volunteer.svg', 5),
  (6, '创新创业', 'types-campus/innovation.svg', 6),
  (7, '校园竞赛', 'types-campus/competition.svg', 7),
  (8, '休闲娱乐', 'types-campus/leisure.svg', 8),
  (9, '场馆预约', 'types-campus/venue.svg', 9),
  (10, '周末活动', 'types-campus/weekend.svg', 10);

INSERT INTO `tb_activity`
  (`id`, `organizer_id`, `name`, `description`, `type_id`, `images`, `area`, `address`, `avg_price`, `open_hours`, `start_time`, `end_time`, `registration_deadline`, `capacity`)
VALUES
  (1, 2, '人工智能前沿讲座', '面向全校学生的人工智能技术分享与交流活动。', 1, '/imgs/campus/lecture-banner.svg', '学术报告厅', '教学楼 A 区一层报告厅', 0, '周三 19:00-21:00', DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY), 200),
  (2, 2, '新生篮球友谊赛', '以球会友，欢迎新同学组队报名参加。', 2, '/imgs/campus/sports-banner.svg', '东区体育场', '东区体育场篮球馆', 0, '周六 14:00-17:00', DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), 80),
  (3, 2, '百团大战社团招新', '集中了解校园社团并现场咨询报名。', 3, '/imgs/campus/club-banner.svg', '青春广场', '青春广场社团服务区', 0, '周日 09:00-17:00', DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 13 DAY), 500);

INSERT INTO `tb_registration_pass`
  (`id`, `activity_id`, `title`, `sub_title`, `rules`, `type`, `status`)
VALUES
  (1, 1, '讲座现场名额', '每位同学限报一次', '报名成功后请按时到场，名额不可转让。', 1, 1),
  (2, 2, '篮球赛参赛名额', '面向在校学生', '请按活动通知完成组队与检录。', 0, 1);

INSERT INTO `tb_limited_registration_quota`
  (`registration_pass_id`, `stock`, `begin_time`, `end_time`)
VALUES
  (1, 200, NOW(), DATE_ADD(NOW(), INTERVAL 6 DAY));
