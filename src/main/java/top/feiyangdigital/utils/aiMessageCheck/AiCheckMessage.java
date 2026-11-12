package top.feiyangdigital.utils.aiMessageCheck;

import cn.hutool.core.util.StrUtil;
import com.unfbx.chatgpt.entity.chat.ChatChoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.entity.BaseInfo;
import top.feiyangdigital.entity.BotRecord;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.handleService.AiCustomerServiceHandler;
import top.feiyangdigital.handleService.MessageHandle;
import top.feiyangdigital.handleService.OpenAiApiService;
import top.feiyangdigital.sqlService.BotRecordService;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.CheckUser;
import top.feiyangdigital.utils.MatchList;
import top.feiyangdigital.utils.groupCaptch.CaptchaManagerCacheMap;
import top.feiyangdigital.utils.groupCaptch.RestrictOrUnrestrictUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AiCheckMessage {

    @Autowired
    private ViolationNoticeAggregator violationNoticeAggregator;

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private MatchList matchList;

    @Autowired
    private MessageHandle messageHandle;

    @Autowired
    private BotRecordService botRecordService;

    @Autowired
    private RestrictOrUnrestrictUser restrictOrUnrestrictUser;

    @Autowired
    private OpenAiApiService openAiApiService;

    @Autowired
    private CheckUser checkUser;

    @Autowired
    private CaptchaManagerCacheMap captchaManagerCacheMap;

    @Autowired
    private AiCustomerServiceHandler aiCustomerServiceHandler;

    public void checkMessage(AbsSender sender, Update update) throws TelegramApiException {
        Message message = update.getMessage();
        // 系统 / 频道来源的消息不是「用户发言」，一律不送 AI 检测：
        // 关联频道自动转发的帖子 from 是 Telegram 服务账号 777000，正文来自群主自己的频道，
        // 送检必然被判成营销广告（实测 spamChance=9），结果是删掉频道帖子、并把 777000 当成用户禁言。
        if (MessageSourceGuard.isSystemOrChannelMessage(message)) {
            log.debug("系统/频道来源消息，跳过AI广告检测。chatId={}",
                    message == null ? null : message.getChatId());
            return;
        }
        String groupId = message.getChatId().toString();
        String userId = message.getFrom().getId().toString();
        Integer messageId = message.getMessageId();
        String firstName = message.getFrom().getFirstName();
        String userName = StrUtil.concat(true, firstName, message.getFrom().getLastName());
        String content = buildAnalyzeContent(userName, message);
        // 机器人账号 / 通过 inline bot（via_bot）发送的消息，是广告号绕过正常检测的典型手法；
        // 这类消息不受「正常发言累积到 5 条后免检」的保护，必须始终送检。
        boolean forceCheck = Boolean.TRUE.equals(message.getFrom().getIsBot()) || message.getViaBot() != null;
        List<KeywordsFormat> keywordsFormatList = matchList.createBanKeyDeleteOptionList(update);
        if (keywordsFormatList != null) {
            if (messageHandle.processUserMessage(sender, update, keywordsFormatList)) {
                return;
            }
        }
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(groupId);
        if (groupInfoWithBLOBs != null && "open".equals(groupInfoWithBLOBs.getAiflag()) && StringUtils.hasText(content)) {
            // 管理员发言不送 AI 广告检测：禁言那一步才查管理员太晚了——
            // 消息在判定为广告时就已经被删掉，管理员自己也救不回来。
            if (isGroupAdminQuietly(sender, groupId, userId)) {
                log.debug("发送者是管理员，跳过AI广告检测。groupId={}, userId={}", groupId, userId);
                return;
            }
            // 群内 @ 本机器人提问时，原消息会被模型当成「推广机器人 + 关键词」判成广告
            //（实测判定理由：包含@xxx机器人账号和"优惠码"，疑似推广某机器人）。
            // 这类消息本应由 AI 客服回答，直接跳过广告检测；上方的关键词删禁规则照常执行。
            if (aiCustomerServiceHandler.isAiCsTriggered(message)) {
                log.debug("消息命中AI客服触发条件，跳过AI广告检测。groupId={}, userId={}", groupId, userId);
                return;
            }
            contentAiOption(sender, groupId, userId, firstName, messageId, content, forceCheck, message.getFrom().getIsBot());
        }
    }

    /**
     * 拼接送给 AI 的检测内容，并把「发布方式」一并告知模型：
     * <ul>
     *     <li>发送者是机器人账号；</li>
     *     <li>消息是通过某个 inline bot（via_bot）发出的——截图里广告号正是用 @StoneOtter 这类机器人投稿。</li>
     * </ul>
     */
    private String buildAnalyzeContent(String userName, Message message) {
        StringBuilder sb = new StringBuilder(userName == null ? "" : userName);
        if (message.getFrom() != null && Boolean.TRUE.equals(message.getFrom().getIsBot())) {
            sb.append("(机器人账号)");
        }
        if (message.getViaBot() != null) {
            String viaName = StringUtils.hasText(message.getViaBot().getUserName())
                    ? "@" + message.getViaBot().getUserName()
                    : message.getViaBot().getFirstName();
            sb.append("(通过机器人 ").append(viaName).append(" 发送)");
        }
        String text = message.getText() == null ? "" : message.getText();
        // 在群里 @ 本机器人属于「向管理机器人提问」，不是推广别人的机器人。
        // 原样送检会让模型把「@机器人 + 优惠码」当成引流广告，这里抹掉提及并显式告知模型。
        String botName = BaseInfo.getBotName();
        if (StringUtils.hasText(botName)) {
            Pattern botMention = Pattern.compile("(?i)@" + Pattern.quote(botName));
            if (botMention.matcher(text).find()) {
                text = botMention.matcher(text).replaceAll(" ").trim();
                sb.append("(其中的 @ 提及是在向本群管理机器人提问，不属于推广或引流)");
            }
        }
        sb.append(",").append(text);
        return sb.toString();
    }

    public void contentAiOption(AbsSender sender, String groupId, String userId, String firstName, Integer messageId, String content) {
        contentAiOption(sender, groupId, userId, firstName, messageId, content, false, null);
    }

    /**
     * @param forceCheck 为 true 时忽略「正常发言累积免检」，始终送 AI 检测（机器人账号 / via_bot 消息）
     */
    public void contentAiOption(AbsSender sender, String groupId, String userId, String firstName, Integer messageId, String content, boolean forceCheck) {
        contentAiOption(sender, groupId, userId, firstName, messageId, content, forceCheck, null);
    }

    /**
     * @param senderIsBot 消息本身携带的「发送者是机器人账号」标志（{@code from.isBot}）。
     *                    传 null 时处置前会回查群成员信息。
     *                    <p>
     *                    优先用消息自带的标志，是因为 {@code GetChatMember} 在目标刚退群 / 网络抖动时会失败：
     *                    回查失败就直接放弃处置，真正还在群里的广告机器人就会漏封。
     */
    public void contentAiOption(AbsSender sender, String groupId, String userId, String firstName, Integer messageId, String content, boolean forceCheck, Boolean senderIsBot) {
        BotRecord botRecord = botRecordService.selBotRecordByGidAndUid(groupId, userId);
        // 原实现在 botRecord == null 时直接跳过整段检测：
        // 任何「没有入群登记」的发送者（尤其是被 @ 拉进群的广告机器人）发的广告都不会被删除。
        int violationCount = (botRecord != null && botRecord.getViolationcount() != null) ? botRecord.getViolationcount() : 0;
        int normalCount = (botRecord != null && botRecord.getNormalcount() != null) ? botRecord.getNormalcount() : 0;
        if (violationCount >= 5) {
            String text = String.format("用户 <b><a href=\"tg://user?id=%d\">%s</a></b> 已被AI检测违规超过5次，永久限制发言！", Long.valueOf(userId), firstName);
            String otherText = String.format("<b>违规用户UserID为：<a href=\"tg://user?id=%d\">%s</a></b>", Long.valueOf(userId), userId);
            boolean punished = notifyViolation(sender, groupId, userId, messageId, text + "\n" + otherText, senderIsBot);
            if (!punished) {
                // 「违规超过 5 次」不受群开关影响，保持原本的永久限制行为
                restrictOrUnrestrictUser.restrictUser(sender, Long.valueOf(userId), groupId, 0L);
            }
            return;
        } else if (!forceCheck && botRecord != null && normalCount >= 5) {
            return;
        }
        List<ChatChoice> list = openAiApiService.getOpenAiAnalyzeResult(content);
        if (!list.isEmpty()) {
            if (list.get(0).getMessage() == null || !StringUtils.hasText(list.get(0).getMessage().getContent())) {
                log.warn("AI检测返回内容为空，跳过本次检测。");
                return;
            }
            String rawContent = list.get(0).getMessage().getContent();
            AiResultParser.SpamResult result = AiResultParser.parseSpam(rawContent);
            if (result == null) {
                log.warn("AI检测返回内容无法解析出spamChance，跳过本次检测。返回内容：{}", rawContent);
                return;
            }
            Integer spamChance = result.getSpamChance();
            String spamReason = result.getSpamReason();
            if (spamChance == null) {
                log.warn("AI检测返回JSON缺少spamChance，跳过本次检测。返回内容：{}", rawContent);
                return;
            }
            BotRecord botRecord1 = new BotRecord();
            if (spamChance >= 6) {
                String text = String.format("用户 <b><a href=\"tg://user?id=%d\">%s</a></b> 已被AI检测发送违规词，判断原因如下：\n<tg-spoiler>%s</tg-spoiler>", Long.valueOf(userId), firstName, spamReason);
                String otherText = String.format("<b>违规用户UserID为：<a href=\"tg://user?id=%d\">%s</a></b>", Long.valueOf(userId), userId);
                // 群开启「AI违规自动禁言」时，命中即永久禁言（文案与解禁按钮由 notifyViolation 统一处理）
                notifyViolation(sender, groupId, userId, messageId, text + "\n" + otherText, senderIsBot);
                botRecord1.setViolationcount(violationCount + 1);
            } else {
                botRecord1.setNormalcount(normalCount + 1);
            }
            botRecord1.setLastmessage(content);
            if (botRecord == null) {
                // 首次见到的发送者（例如刚被拉进群的广告机器人）：补一条记录，后续才能累计违规次数
                botRecordService.addUserRecord(groupId, userId, String.valueOf(System.currentTimeMillis() / 1000));
            }
            botRecordService.updateRecordByGidAndUid(groupId, userId, botRecord1);
        }
    }

    /**
     * AI 判定违规后的统一处置：删消息 + 发通知 + 处置（人类永久禁言 / 广告机器人封禁并移出）。
     * <p>
     * 文本广告（spamChance &gt;= 6）、违规媒体（SafeSearch）都走这里，保证两者行为一致。
     * 禁言成功后，通知消息会带「解除禁言」按钮（复用 BotHelper 里已有的 adminUnmute 回调），
     * 并把通知消息 ID 写入 {@link CaptchaManagerCacheMap}，便于管理员点击解禁时立即删除通知。
     *
     * @return true 表示已执行处置（禁言或封禁）
     */
    public boolean notifyViolation(AbsSender sender, String groupId, String userId, Integer messageId, String text) {
        return notifyViolation(sender, groupId, userId, messageId, text, null);
    }

    /**
     * @param senderIsBot 消息自带的 {@code from.isBot}：非 null 时直接据此判断目标类型，不再回查群成员；
     *                    null 时才回查（{@link #isBotMember}）。
     */
    public boolean notifyViolation(AbsSender sender, String groupId, String userId, Integer messageId, String text, Boolean senderIsBot) {
        // 优先用消息自带的标志：GetChatMember 会因目标刚退群 / 网络抖动而失败，
        // 失败时若直接放弃处置，真正还在群里的广告机器人就会漏封。
        Boolean botTarget = senderIsBot != null ? senderIsBot : isBotMember(sender, groupId, userId);
        // 仍查不到成员信息（系统账号 / 频道身份 / 已退群）时宁可不处置：
        // 原实现把查询失败当成「普通用户」，实测把 Telegram 服务账号 777000 当成用户永久禁言，
        // 并在群里发出「用户 Telegram 已被AI检测发送违规词…已永久禁言该用户」的通知。
        // 注意：无论是否处置，违规消息都会被删除（见 ViolationNoticeAggregator#notifyViolation 第 1 步）。
        boolean punished = botTarget != null && punishViolatorIfEnabled(sender, groupId, userId, botTarget);
        SendMessage notification = new SendMessage();
        notification.setChatId(groupId);
        String actionNote = "";
        if (punished) {
            actionNote = botTarget
                    ? "\n<b>已将该广告机器人移出本群并封禁，管理员可在群成员中手动重新添加。</b>"
                    : "\n<b>已永久禁言该用户，管理员可点击下方按钮解禁。</b>";
        }
        notification.setText(text + actionNote);
        notification.setParseMode(ParseMode.HTML);
        if (punished && !botTarget) {
            notification.setReplyMarkup(buildUnmuteMarkup(userId));
        }
        // 20 秒窗口内的多条违规通知合并成一条（编辑上一条），避免一波广告刷出 4~5 条提醒
        Integer notifyMessageId = violationNoticeAggregator.notifyViolation(sender, groupId, userId, messageId, notification,
                ViolationNoticeAggregator.DEFAULT_DELETE_SECONDS);
        if (punished && !botTarget && notifyMessageId != null && notifyMessageId > 0) {
            captchaManagerCacheMap.updateUserMapping(userId, groupId, 0, notifyMessageId);
        }
        return punished;
    }

    /**
     * 目标是否为机器人账号。
     *
     * @return {@code null} 表示查询失败——通常说明该 ID 不是本群可查询的成员（系统账号、频道身份、已退群），
     * 调用方据此跳过处置，而不是当成普通用户去禁言
     */
    private Boolean isBotMember(AbsSender sender, String groupId, String userId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(groupId);
            getChatMember.setUserId(Long.parseLong(userId));
            ChatMember member = sender.execute(getChatMember);
            return member != null && member.getUser() != null && Boolean.TRUE.equals(member.getUser().getIsBot());
        } catch (Exception e) {
            log.warn("查询目标群成员信息失败，本次不做处置。groupId={}, userId={}, err={}", groupId, userId, e.getMessage());
            return null;
        }
    }

    /** 管理员豁免：查询失败时按非管理员处理（继续检测），避免影响正常检测链路。 */
    private boolean isGroupAdminQuietly(AbsSender sender, String groupId, String userId) {
        try {
            return checkUser.isGroupAdmin(sender, groupId, Long.parseLong(userId));
        } catch (Exception e) {
            log.warn("查询管理员列表失败，按普通成员处理。groupId={}, userId={}, err={}", groupId, userId, e.getMessage());
            return false;
        }
    }

    public boolean muteViolatorIfEnabled(AbsSender sender, String groupId, String userId) {
        return punishViolatorIfEnabled(sender, groupId, userId, false);
    }

    /**
     * 是否对违规目标执行处置（受群级开关 {@code aiMuteFlag} 控制，默认 close）：
     * <ul>
     *     <li>普通用户 → 永久禁言；</li>
     *     <li>机器人 → 封禁并移出（禁言对机器人不可靠，且留在群里只会继续投放广告）；</li>
     *     <li>管理/群主永不被自动处置；查不到管理员列表时宁可不处置。</li>
     * </ul>
     *
     * @return true 表示处置已成功执行
     */
    private boolean punishViolatorIfEnabled(AbsSender sender, String groupId, String userId, boolean botTarget) {
        if (!"open".equals(groupInfoService.selAiMuteFlag(groupId))) {
            return false;
        }
        long uid;
        try {
            uid = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return false;
        }
        // 同一用户 60 秒内只实际处置一次：多广告号往往在同一秒被同一波广告带进来，
        // 每次 RestrictChatMember 都会多出一条「修改了对xxx的限制」系统消息，无法编辑合并。
        if (violationNoticeAggregator.isRecentlyPunished(groupId, userId)) {
            log.warn("60 秒内已处置过该用户，跳过重复禁言/封禁 groupId={}, userId={}", groupId, userId);
            return true;
        }
        if (!botTarget) {
            try {
                if (checkUser.isGroupAdmin(sender, groupId, uid)) {
                    log.warn("AI判定违规的目标是管理员，跳过自动处置。groupId={}, userId={}", groupId, userId);
                    return false;
                }
            } catch (TelegramApiException e) {
                log.warn("查询管理员列表失败，跳过AI自动处置。groupId={}, userId={}, err={}", groupId, userId, e.getMessage());
                return false;
            }
            // seconds = 0 → 永久禁言（项目约定，见 RestrictOrUnrestrictUser.commonFunc）
            boolean muted = restrictOrUnrestrictUser.restrictUser(sender, uid, groupId, 0L);
            if (muted) {
                violationNoticeAggregator.markPunished(groupId, userId);
            }
            log.warn("AI判定违规自动禁言 groupId={}, userId={}, 结果={}", groupId, userId, muted ? "成功（永久）" : "失败");
            return muted;
        }
        boolean banned = banBotMember(sender, groupId, uid);
        if (banned) {
            violationNoticeAggregator.markPunished(groupId, userId);
        }
        log.warn("AI判定违规自动移出机器人 groupId={}, userId={}, 结果={}", groupId, userId, banned ? "成功" : "失败");
        return banned;
    }

    /** 封禁并移出广告机器人（untilDate = 当前时间，即永久）。 */
    private boolean banBotMember(AbsSender sender, String groupId, long uid) {
        BanChatMember banChatMember = new BanChatMember();
        banChatMember.setChatId(groupId);
        banChatMember.setUserId(uid);
        banChatMember.setUntilDateDateTime(java.time.ZonedDateTime.now());
        try {
            sender.execute(banChatMember);
            return true;
        } catch (TelegramApiException e) {
            log.error("封禁广告机器人失败 groupId={}, userId={}, err={}", groupId, uid, e.getMessage());
            return false;
        }
    }

    /** 「解除禁言」按钮：回调复用 BotHelper 里已有的 adminUnmute 前缀。 */
    private static InlineKeyboardMarkup buildUnmuteMarkup(String userId) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("解除禁言");
        button.setCallbackData("adminUnmute" + userId);
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(row));
        return markup;
    }

}
