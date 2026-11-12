package top.feiyangdigital.utils.aiMessageCheck;

import org.telegram.telegrambots.meta.api.objects.Message;

/**
 * 消息来源身份判定：区分「普通用户发言」与「系统 / 频道发言」。
 * <p>
 * 背景（2026-11-10 实踩）：群与频道绑定后，频道每次发帖都会在讨论组里生成一条自动转发消息，
 * 其 {@code from} 是 Telegram 官方服务账号 {@code 777000}（昵称 "Telegram"）、
 * {@code sender_chat} 是本群关联频道、{@code is_automatic_forward = true}。
 * 这条消息被 AI 广告检测当成普通用户发言，判定 spamChance=9
 * 「以双十一活动为名推广 StoneHD 免费 KVM 云服务器，属于明显的商业营销/推广广告」，
 * 于是删掉了帖子、还把 777000 当成"用户"永久禁言并在群里发了处置通知。
 * <p>
 * 系统账号与频道发言都不是「用户」：删除的目标是频道帖子的讨论区镜像，
 * 「禁言」的目标是一个根本不在群成员列表里的虚拟账号。一律不送检测、不做处置。
 */
public final class MessageSourceGuard {

    /** Telegram 官方服务通知账号：群内关联频道自动转发的帖子，其 from 即为该账号。 */
    public static final long SERVICE_NOTICE_ID = 777000L;

    /** 以频道身份发言时 Telegram 使用的虚拟账号（Channel_Bot）。 */
    public static final long CHANNEL_BOT_ID = 136817688L;

    /** 匿名管理员（GroupAnonymousBot）：from.isBot=true，误处置会毁掉群管理。 */
    public static final long ANONYMOUS_ADMIN_ID = 1087968824L;

    private MessageSourceGuard() {
    }

    /**
     * 是否为「非用户发言」——系统通知、关联频道自动转发、以频道身份发言、匿名管理员。
     * <p>
     * 命中后应跳过一切按「用户」维度的处理：防刷计数、昵称/简介检测、AI 广告检测、
     * 广告处置（禁言/封禁），因为目标不是群成员，且正文往往来自群主自己的频道。
     */
    public static boolean isSystemOrChannelMessage(Message message) {
        if (message == null) {
            return true;
        }
        // 关联频道自动转发（is_automatic_forward）与频道身份发言都会带 sender_chat
        if (message.getSenderChat() != null) {
            return true;
        }
        // 直接以频道名义发出的消息没有 from
        if (message.getFrom() == null) {
            return true;
        }
        long uid = message.getFrom().getId();
        return uid == SERVICE_NOTICE_ID || uid == CHANNEL_BOT_ID || uid == ANONYMOUS_ADMIN_ID;
    }
}
