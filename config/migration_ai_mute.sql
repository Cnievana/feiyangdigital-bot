-- ============================================================
-- 迁移脚本：AI 违规自动禁言（群级开关）
-- 适用：已部署过的实例（新装直接用 bot.sql，无需执行本脚本）
--
-- 作用：groupinfo 新增 aiMuteFlag 列，控制「AI 检测到广告/违规媒体后
--       是否立即永久禁言该用户」。
--   open  = 命中即永久禁言（文案附「解除禁言」按钮）
--   close = 保持原行为（只删消息 + 违规计数），默认值
--
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--       若已加过该列会报 1060 Duplicate column name，忽略即可。
-- ============================================================

ALTER TABLE `groupinfo`
    ADD COLUMN `aiMuteFlag` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'close' AFTER `aiFlag`;
