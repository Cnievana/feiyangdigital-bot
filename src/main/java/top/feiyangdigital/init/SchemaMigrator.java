package top.feiyangdigital.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时自动补齐数据库结构（幂等）。
 *
 * <p>背景：{@code bot.sql} 只会在 MySQL 数据卷为空时被 {@code docker-entrypoint-initdb.d}
 * 执行一次。老用户从上游版本升级到本 fork 后，{@code groupinfo} 缺少
 * {@code intoGroupAiCheckFlag}、{@code aiCs*}、{@code aiMuteFlag} 等列，
 * 读取这些开关的 SQL 会直接报 {@code Unknown column}，导致面板整块打不开，
 * 必须手动进容器执行迁移脚本。
 *
 * <p>本类在业务启动（{@code TgBotApplication}）之前运行，逐项检查
 * {@code information_schema}，缺什么补什么，因此：
 * <ul>
 *   <li>全新部署：{@code bot.sql} 已建好，这里什么都不做；</li>
 *   <li>任意老版本升级：自动补齐，无需手动改库；</li>
 *   <li>重复启动：幂等，无副作用。</li>
 * </ul>
 *
 * <p>迁移失败只记日志、不阻断启动（其余功能仍可用），避免
 * 「一个字段没加成导致整个机器人起不来」这种更难排查的故障。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    /** 通用开关列定义（与 config/bot.sql 保持一致）。 */
    private static final String FLAG_CLOSE =
            "varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'close'";

    private static final String FLAG_AT =
            "varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'at'";

    private static final String AI_CS_PROMPT_DDL =
            "CREATE TABLE IF NOT EXISTS `ai_cs_prompt` ("
                    + "  `id` int NOT NULL AUTO_INCREMENT,"
                    + "  `groupId` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,"
                    + "  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,"
                    + "  `prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,"
                    + "  `sortOrder` int NOT NULL DEFAULT '0',"
                    + "  `updateTimestamp` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,"
                    + "  PRIMARY KEY (`id`),"
                    + "  KEY `groupId` (`groupId`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!tableExists("groupinfo")) {
                log.warn("未找到 groupinfo 表，跳过自动迁移。若是全新部署，请确认 bot.sql 已被 MySQL 初始化执行。");
                return;
            }

            int changed = 0;

            // ---- 本 fork 新增：AI 进群昵称/简介检测 ----
            changed += addColumnIfMissing("groupinfo", "intoGroupAiCheckFlag", FLAG_CLOSE, "intoGroupUserNameCheckFlag");

            // ---- 本 fork 新增：AI 智能客服（4 个开关字段）----
            changed += addColumnIfMissing("groupinfo", "aiCsFlag", FLAG_CLOSE, "intoGroupAiCheckFlag");
            changed += addColumnIfMissing("groupinfo", "aiCsTriggerMode", FLAG_AT, "aiCsFlag");
            changed += addColumnIfMissing("groupinfo", "aiCsKeyword",
                    "varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL", "aiCsTriggerMode");
            changed += addColumnIfMissing("groupinfo", "aiCsPrivateFlag", FLAG_CLOSE, "aiCsKeyword");

            // ---- AI 违规自动禁言开关 ----
            changed += addColumnIfMissing("groupinfo", "aiMuteFlag", FLAG_CLOSE, "aiFlag");

            // ---- AI 检测总开关（更老的库可能缺）----
            changed += addColumnIfMissing("groupinfo", "aiFlag", FLAG_CLOSE, "channelSpamFlag");

            // ---- 本 fork 新增：AI 客服知识条目表 ----
            if (!tableExists("ai_cs_prompt")) {
                jdbcTemplate.execute(AI_CS_PROMPT_DDL);
                changed++;
                log.info("自动迁移：已创建表 ai_cs_prompt");
            }

            if (changed == 0) {
                log.info("数据库结构检查完成，无需迁移。");
            } else {
                log.info("数据库结构检查完成，自动补齐 {} 项变更。", changed);
            }
        } catch (Exception e) {
            log.error("数据库自动迁移出现异常，机器人仍会继续启动；"
                    + "若相关功能异常，请手动执行 config/migration_*.sql", e);
        }
    }

    /**
     * 列不存在时才添加。AFTER 指定的锚点列若不存在，则降级为「追加到表末尾」，
     * 避免因老库缺少锚点列导致整条 ALTER 失败（列顺序不影响业务，代码均按列名读写）。
     *
     * @return 实际执行了 DDL 返回 1，否则返回 0
     */
    private int addColumnIfMissing(String table, String column, String definition, String afterColumn) {
        if (columnExists(table, column)) {
            return 0;
        }
        StringBuilder sql = new StringBuilder()
                .append("ALTER TABLE `").append(table).append("` ADD COLUMN `").append(column)
                .append("` ").append(definition);
        if (afterColumn != null && !afterColumn.isEmpty() && columnExists(table, afterColumn)) {
            sql.append(" AFTER `").append(afterColumn).append("`");
        }
        try {
            jdbcTemplate.execute(sql.toString());
            log.info("自动迁移：groupinfo 已新增字段 {}", column);
            return 1;
        } catch (Exception e) {
            log.error("自动迁移失败：{}（请手动执行 config/migration_*.sql 补齐该字段）", sql, e);
            return 0;
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }
}
