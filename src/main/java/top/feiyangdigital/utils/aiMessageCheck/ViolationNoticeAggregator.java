package top.feiyangdigital.utils.aiMessageCheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.utils.TimerDelete;
import top.feiyangdigital.utils.groupCaptch.CaptchaManager;
import top.feiyangdigital.utils.groupCaptch.CaptchaManagerCacheMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 违规通知聚合器。
 * <p>
 * 背景：一次刷屏广告往往由同一波广告号在几秒内同时发出（同一条消息 @ 一批账号），
 * 原实现「一条广告 = 一条通知」，群里会被 4~5 条几乎相同格式的处置提示刷屏，
 * 有用信息被淹没。
 * <p>
 * 现在的行为：
 * <ul>
 *     <li>按<b>群</b>聚合：同一群 <b>20 秒</b>内的后续违规通知不再新发消息，而是把内容
 *         <b>追加到上一条通知并原地编辑</b>（使用 EditMessageText）；</li>
 *     <li>按钮（如「解除禁言」）会合并到同一条消息上，文本重复时自动补上用户 ID 以便区分；</li>
 *     <li>通知在「最后一次更新」后 <b>30 秒</b>删除（窗口内持续有违规时会自动顺延）；</li>
 *     <li>同一群的处理必须串行：同一秒并发进来的多条广告若各自「先查上一条再发送」，
 *         会同时判定为「没有上一条」而各发一条，因此这里用分桶锁把「查窗口 → 编辑/发送」
 *         整段包起来。</li>
 * </ul>
 */
@Slf4j
@Component
public class ViolationNoticeAggregator {

    /** 合并窗口：同群 20 秒内（从最后一次更新算起）的违规通知合并进同一条消息。 */
    public static final long MERGE_WINDOW_MS = 20_000L;

    /** 单条汇总消息最多容纳的违规条数，避免超出 Telegram 的 4096 字符与键盘按钮上限。 */
    private static final int MAX_ENTRIES = 12;

    /** 合并后文本长度上限（Telegram 硬上限 4096，留出余量）。 */
    private static final int MAX_TEXT_LENGTH = 3800;

    /** 单条消息最多容纳的按钮行数。 */
    private static final int MAX_BUTTONS = 20;

    /** 按钮文本硬上限（Telegram 限制 64 字节，这里按字符保守截断）。 */
    private static final int MAX_BUTTON_TEXT = 60;

    /**
     * 通知默认保留时间（秒）：从最后一次更新起算。
     * 必须大于合并窗口，否则窗口内的通知会在还能被编辑前就被删掉。
     */
    public static final int DEFAULT_DELETE_SECONDS = 30;

    /**
     * 通知最短保留时间：必须大于合并窗口，否则窗口内的通知会在编辑前被删掉。
     * 调用方传入的值更大时以调用方为准。
     */
    private static final int MIN_DELETE_SECONDS = 30;

    /** 分桶锁数量：按 chatId 哈希分桶，避免为每个群长期持有锁对象。 */
    private static final int LOCK_STRIPES = 64;

    /** 同一用户重复处置的去重窗口：昵称不会在一秒内改变，同一波广告也不应重复禁言。 */
    private static final long PUNISH_DEDUP_MS = 60_000L;

    private final Object[] locks = new Object[LOCK_STRIPES];

    /** key = chatId。 */
    private final Map<String, Notice> notices = new ConcurrentHashMap<>();

    /** key = chatId|userId，记录最近一次实际处置时间。 */
    private final Map<String, Long> punishedAt = new ConcurrentHashMap<>();

    private static final Pattern TRAILING_DIGITS = Pattern.compile("(\\d+)$");

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private TimerDelete timerDelete;

    @Autowired
    private CaptchaManager captchaManager;

    @Autowired
    private CaptchaManagerCacheMap captchaManagerCacheMap;

    public ViolationNoticeAggregator() {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    /**
     * 60 秒内是否已经实际处置过该用户。
     * <p>
     * 用途：同一用户的多条广告（往往是同一秒发出）会各自走一遍处置流程，
     * 每次 {@code RestrictChatMember} 都会让 Telegram 生成一条「修改了对xxx的限制」系统消息，
     * 这些系统消息无法编辑或合并，只能从源头减少重复调用。
     */
    public boolean isRecentlyPunished(String chatId, String userId) {
        Long last = punishedAt.get(chatId + "|" + userId);
        return last != null && System.currentTimeMillis() - last < PUNISH_DEDUP_MS;
    }

    /** 记录一次实际处置成功（禁言 / 封禁）。 */
    public void markPunished(String chatId, String userId) {
        long now = System.currentTimeMillis();
        punishedAt.put(chatId + "|" + userId, now);
        // 低频清理，避免机器人长期运行后 map 无限增长
        if (punishedAt.size() > 2000) {
            punishedAt.entrySet().removeIf(entry -> now - entry.getValue() > PUNISH_DEDUP_MS);
        }
    }

    /**
     * 违规处置后的统一入口：删除违规消息 + 清理验证缓存 + 合并发送通知。
     *
     * @param userId            违规用户 ID（仅用于清理验证缓存）
     * @param violationMessageId 被处置的违规消息 ID，可为 null（例如入群时检测昵称）
     * @return 通知消息 ID；发送失败返回 null
     */
    public Integer notifyViolation(AbsSender sender, String chatId, String userId, Integer violationMessageId,
                                   SendMessage template, int deleteAfterSeconds) {
        // 1) 删除违规消息：失败（常见于群里还有别的管理机器人已抢先删除）不能影响后续通知
        if (violationMessageId != null) {
            boolean deleted = timerDelete.deleteByMessageIdImmediately(sender, chatId, violationMessageId);
            if (!deleted) {
                log.warn("删除违规消息失败（可能已被其他机器人删除）chatId={}, messageId={}", chatId, violationMessageId);
            }
        }
        // 2) 清理验证缓存：与删除解耦，保证「为什么被处置」一定说得清楚
        try {
            captchaManager.clearMappingsForUser(userId);
            captchaManagerCacheMap.clearMappingsForUser(userId, chatId);
        } catch (Exception e) {
            log.warn("清理验证缓存失败 userId={}, chatId={}, err={}", userId, chatId, e.getMessage());
        }
        // 3) 合并发送通知
        return notify(sender, chatId, template, deleteAfterSeconds);
    }

    /**
     * 把一条通知并入当前群最近一条通知；窗口已过期则新发一条。
     *
     * @return 通知消息 ID（合并时为上一条消息的 ID）；发送失败返回 null
     */
    public Integer notify(AbsSender sender, String chatId, SendMessage template, int deleteAfterSeconds) {
        if (template == null || !StringUtils.hasText(template.getText())) {
            return null;
        }
        template.setChatId(chatId);
        String line = template.getText();
        InlineKeyboardMarkup markup = inlineMarkupOf(template.getReplyMarkup());
        String parseMode = template.getParseMode();

        // ★ 关键：同一群的「读窗口 + 编辑/发送」必须整体串行。
        // 否则同一秒并发进来的多条广告会各自读到「没有上一条」，各发一条消息（正是当前现象）。
        synchronized (lockFor(chatId)) {
            long now = System.currentTimeMillis();
            Notice notice = notices.get(chatId);
            if (notice != null && now - notice.lastUpdateMs > MERGE_WINDOW_MS) {
                // 窗口已过期：上一条通知进入「只等待删除」状态
                notices.remove(chatId);
                notice = null;
            }

            if (notice != null) {
                // 完全相同的文案（同一违规被重复触发）不再重复追加，避免同一条消息里出现重复段落
                if (notice.entries < MAX_ENTRIES && notice.text.contains(line)) {
                    notice.lastUpdateMs = now;
                    scheduleDelete(sender, chatId, notice, deleteAfterSeconds);
                    return notice.messageId;
                }
                String mergedText = notice.text + "\n\n" + line;
                InlineKeyboardMarkup mergedMarkup = mergeMarkup(notice.markup, markup);
                if (notice.entries < MAX_ENTRIES && mergedText.length() <= MAX_TEXT_LENGTH
                        && editMessage(sender, chatId, notice.messageId, mergedText, mergedMarkup, parseMode)) {
                    notice.text = mergedText;
                    notice.markup = mergedMarkup;
                    notice.entries++;
                    notice.lastUpdateMs = now;
                    scheduleDelete(sender, chatId, notice, deleteAfterSeconds);
                    log.debug("违规通知已合并 chatId={}, messageId={}, entries={}", chatId, notice.messageId, notice.entries);
                    return notice.messageId;
                }
                // 编辑失败（消息已被管理员删除 / 内容未变化 / 超限）：退回新发一条
                log.debug("违规通知合并失败，改为新发 chatId={}, 上一条 messageId={}", chatId, notice.messageId);
            }

            Integer sentId = sendMessage(sender, template);
            if (sentId == null) {
                return null;
            }
            Notice fresh = new Notice(sentId, line, markup, now);
            notices.put(chatId, fresh);
            scheduleDelete(sender, chatId, fresh, deleteAfterSeconds);
            return sentId;
        }
    }

    private Object lockFor(String chatId) {
        int index = (chatId.hashCode() & 0x7fffffff) % LOCK_STRIPES;
        return locks[index];
    }

    private Integer sendMessage(AbsSender sender, SendMessage template) {
        try {
            Message sent = sender.execute(template);
            return sent == null ? null : sent.getMessageId();
        } catch (TelegramApiException e) {
            log.error("发送违规通知失败 chatId={}, err={}", template.getChatId(), e.getMessage());
            return null;
        }
    }

    private boolean editMessage(AbsSender sender, String chatId, Integer messageId, String text,
                                InlineKeyboardMarkup markup, String parseMode) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId);
        edit.setMessageId(messageId);
        edit.setText(text);
        if (StringUtils.hasText(parseMode)) {
            edit.setParseMode(parseMode);
        }
        // 空键盘 = 清除按钮；不能传 null（null 表示「保持原按钮」）
        edit.setReplyMarkup(markup == null ? emptyMarkup() : markup);
        try {
            sender.execute(edit);
            return true;
        } catch (TelegramApiException e) {
            log.debug("编辑违规通知失败 chatId={}, messageId={}, err={}", chatId, messageId, e.getMessage());
            return false;
        }
    }

    private static InlineKeyboardMarkup emptyMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(new ArrayList<>());
        return markup;
    }

    private static InlineKeyboardMarkup inlineMarkupOf(Object keyboard) {
        return keyboard instanceof InlineKeyboardMarkup ? (InlineKeyboardMarkup) keyboard : null;
    }

    /**
     * 合并键盘：按 callbackData / url / text 去重，一个按钮一行。
     * 文本重复时（合并后会有多个「解除禁言」）自动补上用户 ID，避免管理员分不清点的是谁。
     */
    private static InlineKeyboardMarkup mergeMarkup(InlineKeyboardMarkup existing, InlineKeyboardMarkup incoming) {
        Map<String, InlineKeyboardButton> merged = new LinkedHashMap<>();
        collectButtons(merged, existing);
        collectButtons(merged, incoming);
        if (merged.isEmpty()) {
            return null;
        }
        Collection<InlineKeyboardButton> buttons = merged.values();
        Map<String, Integer> textCount = new HashMap<>();
        for (InlineKeyboardButton button : buttons) {
            String text = button.getText() == null ? "" : button.getText();
            Integer count = textCount.get(text);
            textCount.put(text, count == null ? 1 : count + 1);
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (InlineKeyboardButton button : buttons) {
            if (rows.size() >= MAX_BUTTONS) {
                break;
            }
            String text = button.getText() == null ? "" : button.getText();
            Integer count = textCount.get(text);
            InlineKeyboardButton copy = copyOf(button);
            if (count != null && count > 1) {
                String suffix = trailingDigits(button);
                copy.setText(truncate((text + (suffix == null ? "" : " " + suffix)).trim(), MAX_BUTTON_TEXT));
            }
            rows.add(new ArrayList<>(Collections.singletonList(copy)));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private static void collectButtons(Map<String, InlineKeyboardButton> target, InlineKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return;
        }
        for (List<InlineKeyboardButton> row : markup.getKeyboard()) {
            if (row == null) {
                continue;
            }
            for (InlineKeyboardButton button : row) {
                if (button == null) {
                    continue;
                }
                target.putIfAbsent(buttonKey(button), button);
            }
        }
    }

    private static String buttonKey(InlineKeyboardButton button) {
        if (StringUtils.hasText(button.getCallbackData())) {
            return "cb:" + button.getCallbackData();
        }
        if (StringUtils.hasText(button.getUrl())) {
            return "url:" + button.getUrl();
        }
        return "text:" + button.getText();
    }

    private static InlineKeyboardButton copyOf(InlineKeyboardButton source) {
        InlineKeyboardButton copy = new InlineKeyboardButton();
        copy.setText(source.getText());
        copy.setCallbackData(source.getCallbackData());
        copy.setUrl(source.getUrl());
        return copy;
    }

    /** 从 callbackData（形如 adminUnmute8494647793）取尾部数字，用于区分同名按钮。 */
    private static String trailingDigits(InlineKeyboardButton button) {
        String callbackData = button.getCallbackData();
        if (!StringUtils.hasText(callbackData)) {
            return null;
        }
        Matcher matcher = TRAILING_DIGITS.matcher(callbackData);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 调度删除：只让「该通知的最新一次更新」对应的任务生效。
     * 窗口内每追加一条都会重新调度，旧任务因 version 不匹配而跳过，避免把刚合并好的消息提前删掉。
     * <p>
     * 注意：任务只依赖 notice 自身的 version，不依赖它是否仍是 map 里的当前值——
     * 窗口过期后新建的通知会覆盖 map，旧消息依然要按原计划删除，否则会永久留在群里。
     */
    private void scheduleDelete(AbsSender sender, String chatId, Notice notice, int deleteAfterSeconds) {
        notice.version++;
        final int version = notice.version;
        final Integer messageId = notice.messageId;
        int seconds = Math.max(deleteAfterSeconds, MIN_DELETE_SECONDS);
        taskScheduler.schedule(() -> {
            synchronized (lockFor(chatId)) {
                if (notice.version != version) {
                    // 期间又被合并过，交给最新的任务处理
                    return;
                }
                // 仅当 map 里仍然是这条通知时才移除，避免误删后来新建的聚合状态
                notices.remove(chatId, notice);
                timerDelete.deleteByMessageIdImmediately(sender, chatId, messageId);
            }
        }, Instant.now().plusSeconds(seconds));
    }

    /** 当前群的聚合状态。 */
    private static final class Notice {
        private final Integer messageId;
        private String text;
        private InlineKeyboardMarkup markup;
        private int entries;
        private long lastUpdateMs;
        private int version;

        private Notice(Integer messageId, String text, InlineKeyboardMarkup markup, long lastUpdateMs) {
            this.messageId = messageId;
            this.text = text;
            this.markup = markup;
            this.lastUpdateMs = lastUpdateMs;
            this.entries = 1;
        }
    }
}
