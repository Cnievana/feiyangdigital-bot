-- =============================================================================
-- AI 客服功能 · 数据库迁移脚本
-- 适用：已部署的旧库升级（全新安装直接用 config/bot.sql，无需执行本脚本）
--
-- 用法：
--   docker exec -i 你的mysql容器名 mysql -u root -p密码 bot < migration_ai_customer_service.sql
--
-- ⚠️ MySQL 不支持 ADD COLUMN IF NOT EXISTS，若字段已存在会报 1060 错误，
--    可忽略该错误（说明已经迁移过）。
-- =============================================================================

-- 1) groupinfo 增加 AI 客服相关字段
ALTER TABLE `groupinfo`
    ADD COLUMN `aiCsFlag`        varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'close' AFTER `intoGroupAiCheckFlag`,
    ADD COLUMN `aiCsTriggerMode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'at'    AFTER `aiCsFlag`,
    ADD COLUMN `aiCsKeyword`     varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci         DEFAULT NULL   AFTER `aiCsTriggerMode`,
    ADD COLUMN `aiCsPrivateFlag` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'close' AFTER `aiCsKeyword`;

-- 2) 新建 AI 客服知识条目表
CREATE TABLE IF NOT EXISTS `ai_cs_prompt`
(
    `id`              int                                                          NOT NULL AUTO_INCREMENT,
    `groupId`         varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `title`           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `prompt`          text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci        NOT NULL,
    `sortOrder`       int                                                          NOT NULL DEFAULT '0',
    `updateTimestamp` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci          DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `groupId` (`groupId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 3) 校验
-- SHOW COLUMNS FROM groupinfo LIKE 'aiCs%';
-- SHOW TABLES LIKE 'ai_cs_prompt';
