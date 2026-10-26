package top.feiyangdigital.utils.aiMessageCheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.entity.BaseInfo;
import top.feiyangdigital.utils.CheckUser;
import top.feiyangdigital.utils.TimerDelete;
import top.feiyangdigital.utils.groupCaptch.RestrictOrUnrestrictUser;

import java.time.ZonedDateTime;

/**
 * 广告机器人专属守卫。
 * <p>
 * 目标：让「机器人发送 / 通过机器人发送」的群聊正文可以由关键词规则直接处置，
 * 命中后立刻删除消息并封禁，<b>完全不调用 AI 检测</b>（0 token），
 * 同时因为只作用于 bot，普通用户不会被这套规则误伤。
 * <p>
 * 处置全程静默，不在群里发送任何通知（通知由后续的汇总机制统一发送）。
 */
@Slf4j
@Component
public class BotSpamGuard {

    /**
     * 匿名管理员（GroupAnonymousBot）。它的 {@code from.isBot} 也是 true，
     * 如果只按 isBot 判断，会把群里的匿名管理员当成广告机器人封掉。
     */
    private static final long ANONYMOUS_ADMIN_ID = 1087968824L;

    /** 频道身份发言（Channel_Bot）。封掉它会破坏群与频道的绑定关系。 */
    private static final long CHANNEL_BOT_ID = 136817688L;

    /** Telegram 官方服务通知账号（入群、退群等系统消息）。 */
    private static final long SERVICE_NOTICE_ID = 777000L;

    @Autowired
    private CheckUser checkUser;

    @Autowired
    private TimerDelete timerDelete;

    @Autowired
    private RestrictOrUnrestrictUser restrictOrUnrestrictUser;

    /**
     * 判断消息是否来自「机器人账号」或「通过 inline bot 发送（via_bot）」。
     * <p>
     * 会排除以下几类「看起来像 bot、但绝不能处置」的发送者：
     * <ul>
     *     <li>匿名管理员（from.isBot = true）；</li>
     *     <li>以频道身份发言（from = Channel_Bot）；</li>
     *     <li>Telegram 系统服务通知；</li>
     *     <li>本机器人自己（防止规则命中自己的面板/通知而自我封禁）。</li>
     * </ul>
     */
    public boolean isBotSender(Message message) {
        if (message == null || message.getFrom() == null) {
            return false;
        }
        long uid = message.getFrom().getId();
        if (uid == ANONYMOUS_ADMIN_ID || uid == CHANNEL_BOT_ID || uid == SERVICE_NOTICE_ID) {
            return false;
        }
        String botName = BaseInfo.getBotName();
        if (StringUtils.hasText(botName) && botName.equalsIgnoreCase(message.getFrom().getUserName())) {
            return false;
        }
        boolean botAccount = Boolean.TRUE.equals(message.getFrom().getIsBot());
        boolean viaBot = message.getViaBot() != null;
        return botAccount || viaBot;
    }

    /**
     * botBan 规则命中后的处置：删消息 → 封禁（机器人）/ 永久禁言（人类 via bot）。
     * <p>
     * 静默执行，不发任何群内通知。管理员与其管理的机器人只删消息、不做处置。
     *
     * @param ruleUuid           命中的规则 UUID，仅用于日志追踪
     * @param deleteAfterSeconds 命中后多少秒删除原消息（0 = 立即；解析失败也按立即处理）
     * @return 恒为 true，调用方据此终止后续检测（从而跳过 AI，省 token）
     */
    public boolean punish(AbsSender sender, String chatId, Integer messageId, Message message,
                          String ruleUuid, String deleteAfterSeconds) {
        // 1) 先删消息：即使后面的查询/封禁失败（权限不足、超时），广告内容也已经从群里消失
        int delay = 0;
        try {
            delay = Integer.parseInt(String.valueOf(deleteAfterSeconds).trim());
        } catch (NumberFormatException ignored) {
            delay = 0;
        }
        if (delay <= 0) {
            if (!timerDelete.deleteByMessageIdImmediately(sender, chatId, messageId)) {
                log.warn("botBan 规则命中，但删除消息失败（可能已被其他管理机器人删除或无删除权限）。chatId={}, messageId={}",
                        chatId, messageId);
            }
        } else {
            timerDelete.deleteMessageByMessageIdDelay(sender, chatId, messageId, delay);
        }
        if (message == null || message.getFrom() == null) {
            return true;
        }
        long uid = message.getFrom().getId();
        String name = describeSender(message);
        String viaNote = describeViaBot(message);
        boolean botAccount = Boolean.TRUE.equals(message.getFrom().getIsBot());

        // 2) 管理员（以及管理员身份入群的管理机器人）只删消息、不处置
        try {
            if (checkUser.isGroupAdmin(sender, chatId, uid)) {
                log.warn("botBan 规则命中管理员身份，仅删除消息。chatId={}, uid={}, 名称={}, 规则={}",
                        chatId, uid, name, ruleUuid);
                return true;
            }
        } catch (TelegramApiException e) {
            log.warn("botBan 查询管理员列表失败，保守起见仅删除消息。chatId={}, uid={}, err={}",
                    chatId, uid, e.getMessage());
            return true;
        }

        // 3) 机器人账号 → 封禁并移出；人类通过 inline bot 发广告 → 永久禁言
        if (botAccount) {
            boolean banned = banBot(sender, chatId, uid);
            log.warn("botBan 命中广告机器人 -> 封禁并移出 chatId={}, uid={}, 名称={}, 结果={}, 规则={}",
                    chatId, uid, name, banned ? "成功" : "失败", ruleUuid);
        } else {
            boolean muted = restrictOrUnrestrictUser.restrictUser(sender, uid, chatId, 0L);
            log.warn("botBan 命中 via bot 广告 -> 永久禁言 chatId={}, uid={}, 名称={}, 结果={}, 规则={}, {}",
                    chatId, uid, name, muted ? "成功" : "失败", ruleUuid, viaNote);
        }
        return true;
    }

    /** 封禁并移出（untilDate = 当前时间，即永久）。 */
    private boolean banBot(AbsSender sender, String chatId, long uid) {
        BanChatMember banChatMember = new BanChatMember();
        banChatMember.setChatId(chatId);
        banChatMember.setUserId(uid);
        banChatMember.setUntilDateDateTime(ZonedDateTime.now());
        try {
            sender.execute(banChatMember);
            return true;
        } catch (TelegramApiException e) {
            log.error("botBan 封禁广告机器人失败 chatId={}, uid={}, err={}", chatId, uid, e.getMessage());
            return false;
        }
    }

    /** 日志用的发送者展示名：优先 @username，其次昵称，最后 uid。 */
    private static String describeSender(Message message) {
        String userName = message.getFrom().getUserName();
        if (StringUtils.hasText(userName)) {
            return "@" + userName;
        }
        String firstName = message.getFrom().getFirstName();
        return StringUtils.hasText(firstName) ? firstName : String.valueOf(message.getFrom().getId());
    }

    /** 日志用的 via_bot 说明。 */
    private static String describeViaBot(Message message) {
        if (message.getViaBot() == null) {
            return "";
        }
        String viaName = StringUtils.hasText(message.getViaBot().getUserName())
                ? "@" + message.getViaBot().getUserName()
                : message.getViaBot().getFirstName();
        return "通过机器人 " + viaName + " 发送";
    }
}
