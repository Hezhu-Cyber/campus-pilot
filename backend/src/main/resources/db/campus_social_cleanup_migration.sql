-- 已有 CampusPilot 数据库的可选清理脚本。
-- 执行前请备份；脚本移除已下线的社交、每日打卡和未使用资料字段。
DROP TABLE IF EXISTS `tb_user_follow`;
DROP TABLE IF EXISTS `tb_sign`;

SET @drop_fans = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_info' AND COLUMN_NAME = 'fans') > 0,
  'ALTER TABLE tb_user_info DROP COLUMN fans',
  'SELECT 1'
);
PREPARE statement FROM @drop_fans;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @drop_credits = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_info' AND COLUMN_NAME = 'credits') > 0,
  'ALTER TABLE tb_user_info DROP COLUMN credits',
  'SELECT 1'
);
PREPARE statement FROM @drop_credits;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @drop_level = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_info' AND COLUMN_NAME = 'level') > 0,
  'ALTER TABLE tb_user_info DROP COLUMN level',
  'SELECT 1'
);
PREPARE statement FROM @drop_level;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @drop_followee = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_info' AND COLUMN_NAME = 'followee') > 0,
  'ALTER TABLE tb_user_info DROP COLUMN followee',
  'SELECT 1'
);
PREPARE statement FROM @drop_followee;
EXECUTE statement;
DEALLOCATE PREPARE statement;
