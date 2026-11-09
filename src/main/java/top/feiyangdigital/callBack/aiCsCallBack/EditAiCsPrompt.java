package top.feiyangdigital.callBack.aiCsCallBack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.entity.AiCsPrompt;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.sqlService.AiCsPromptService;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.SendContent;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;
import top.feiyangdigital.utils.ruleCacheMap.AiCsCacheMap;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 客服：知识条目的查看与修改。
 * <p>
 * 回调数据格式：
 * <ul>
 *     <li>{@code aiCsView<id>} —— 查看条目详情（名称 + 正文）</li>
 *     <li>{@code aiCsEditTitle<id>} —— 等待输入新的条目名称</li>
 *     <li>{@code aiCsEditContent<id>} —— 等待输入新的条目正文</li>
 * </ul>
 * 等待输入状态通过 {@link AiCsCacheMap} 打标（flag 中带上主键），
 * 再由 {@code CommonFunction} 按 flag 前缀分发给 {@link #saveTitle} / {@link #saveContent}。
 *
 * @author Cnievana
 */
@Component
public class EditAiCsPrompt {

    /** 等待输入「条目名称」的 flag 前缀，完整形态：editPromptTitle:12 */
    public static final String FLAG_TITLE_PREFIX = "editPromptTitle:";
    /** 等待输入「条目正文」的 flag 前缀，完整形态：editPromptContent:12 */
    public static final String FLAG_CONTENT_PREFIX = "editPromptContent:";

    /** 条目名称长度上限（与 ai_cs_prompt.title 的使用习惯保持一致） */
    private static final int MAX_TITLE_LENGTH = 50;
    /** 条目正文长度上限（Telegram 单条消息 4096，留出余量） */
    private static final int MAX_PROMPT_LENGTH = 3800;
    /**
     * 详情页最多展示的正文长度（按转义后的字符数计算）。
     * 正文里 &lt; &gt; &amp; 会被转义成 4~5 个字符，因此必须按转义后的长度限制，
     * 否则一条含大量尖括号的提示词会把消息顶到 4096 上限而发送失败。
     */
    private static final int MAX_BODY_PREVIEW = 3400;

    @Autowired
    private SendContent sendContent;

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private AiCsPromptService aiCsPromptService;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    @Autowired
    private AiCsCacheMap aiCsCacheMap;

    /** 查看条目详情。 */
    public void showDetail(AbsSender sender, Update update) throws TelegramApiException {
        Integer id = parseCallbackId(update, "aiCsView");
        if (id == null) {
            return;
        }
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        // 从「等待输入」状态点返回时，必须清掉 flag，
        // 否则下一条私聊文本会被当成新的名称/正文。
        aiCsCacheMap.clearFlag(userId);
        AiCsPrompt prompt = aiCsPromptService.selByIdAndGroupId(id, groupId);
        sender.execute(sendContent.editResponseMessage(update, buildDetail(prompt, id, null), "html"));
    }

    /** 提示管理员输入新的条目名称。 */
    public void promptEditTitle(AbsSender sender, Update update) throws TelegramApiException {
        Integer id = parseCallbackId(update, "aiCsEditTitle");
        if (id == null) {
            return;
        }
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        if (rejectIfExpired(sender, update, userId, groupId)) {
            return;
        }
        AiCsPrompt target = aiCsPromptService.selByIdAndGroupId(id, groupId);
        if (target == null) {
            sender.execute(sendContent.editResponseMessage(update, buildDetail(null, id, null), "html"));
            return;
        }
        aiCsCacheMap.updateFlag(userId, FLAG_TITLE_PREFIX + id);

        List<String> buttons = new ArrayList<>();
        buttons.add("◀️返回条目##aiCsView" + id + "%%❌关闭菜单##closeMenu");
        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText("⚡️请直接在输入框里输入新的<b>条目名称</b>（最多 " + MAX_TITLE_LENGTH + " 字）。\n"
                + "当前名称：<b>" + escapeHtml(target.getTitle()) + "</b>\n\n"
                + "名称只用于面板区分，不会发给群成员。\n\n"
                + "⏱ 设置有效期 15 分钟，未超时期间每点一次按钮都会重新计时；一旦超时请在私聊发送 /start 重新选择群组。⚡️");
        format.setKeywordsButtons(buttons);
        sender.execute(sendContent.editResponseMessage(update, format, "html"));
    }

    /** 提示管理员输入新的条目正文。 */
    public void promptEditContent(AbsSender sender, Update update) throws TelegramApiException {
        Integer id = parseCallbackId(update, "aiCsEditContent");
        if (id == null) {
            return;
        }
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        if (rejectIfExpired(sender, update, userId, groupId)) {
            return;
        }
        AiCsPrompt target = aiCsPromptService.selByIdAndGroupId(id, groupId);
        if (target == null) {
            sender.execute(sendContent.editResponseMessage(update, buildDetail(null, id, null), "html"));
            return;
        }
        aiCsCacheMap.updateFlag(userId, FLAG_CONTENT_PREFIX + id);

        List<String> buttons = new ArrayList<>();
        buttons.add("◀️返回条目##aiCsView" + id + "%%❌关闭菜单##closeMenu");
        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText("⚡️请直接在输入框里输入新的<b>条目正文</b>（最多 " + MAX_PROMPT_LENGTH + " 字）。\n"
                + "当前条目：<b>" + escapeHtml(target.getTitle()) + "</b>\n\n"
                + "⚠️输入的内容会<b>整段覆盖</b>原有正文，不会追加。\n"
                + "支持多行：直接换行即可，换行会原样保留。\n\n"
                + "⏱ 设置有效期 15 分钟，未超时期间每点一次按钮都会重新计时；一旦超时请在私聊发送 /start 重新选择群组。⚡️");
        format.setKeywordsButtons(buttons);
        sender.execute(sendContent.editResponseMessage(update, format, "html"));
    }

    /** 处理管理员输入的新名称（flag = editPromptTitle:&lt;id&gt;）。 */
    public void saveTitle(AbsSender sender, Update update) throws TelegramApiException {
        String text = update.getMessage().getText();
        if (text == null) {
            return;
        }
        String userId = update.getMessage().getFrom().getId().toString();
        Integer id = parseFlagId(aiCsCacheMap.getFlagForUser(userId), FLAG_TITLE_PREFIX);
        if (id == null) {
            return;
        }
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        if (groupId == null) {
            aiCsCacheMap.clearFlag(userId);
            return;
        }
        if (groupInfoService.isSettingExpired(groupId)) {
            aiCsCacheMap.clearFlag(userId);
            sender.execute(sendContent.messageText(update, "⏱ 本次设置已超时（有效期 15 分钟）。\n请在私聊里发送 /start 重新选择群组，即可继续设置。"));
            return;
        }

        String title = text.trim();
        if (title.isEmpty()) {
            sender.execute(sendContent.messageText(update, "❌名称不能为空，请重新输入。"));
            return;
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        AiCsPrompt target = aiCsPromptService.selByIdAndGroupId(id, groupId);
        if (target == null) {
            aiCsCacheMap.clearFlag(userId);
            sender.execute(sendContent.messageText(update, "⚠️该条目已不存在（可能已被删除），请返回列表刷新。"));
            return;
        }
        if (aiCsPromptService.updateTitle(id, groupId, title)) {
            aiCsCacheMap.clearFlag(userId);
            AiCsPrompt updated = aiCsPromptService.selByIdAndGroupId(id, groupId);
            sender.execute((SendMessage) sendContent.createResponseMessage(update, buildDetail(updated, id, "✅名称已更新"), "html"));
        } else {
            sender.execute(sendContent.messageText(update, "❌保存失败，请重试。"));
        }
    }

    /** 处理管理员输入的新正文（flag = editPromptContent:&lt;id&gt;）。 */
    public void saveContent(AbsSender sender, Update update) throws TelegramApiException {
        String text = update.getMessage().getText();
        if (text == null) {
            return;
        }
        String userId = update.getMessage().getFrom().getId().toString();
        Integer id = parseFlagId(aiCsCacheMap.getFlagForUser(userId), FLAG_CONTENT_PREFIX);
        if (id == null) {
            return;
        }
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        if (groupId == null) {
            aiCsCacheMap.clearFlag(userId);
            return;
        }
        if (groupInfoService.isSettingExpired(groupId)) {
            aiCsCacheMap.clearFlag(userId);
            sender.execute(sendContent.messageText(update, "⏱ 本次设置已超时（有效期 15 分钟）。\n请在私聊里发送 /start 重新选择群组，即可继续设置。"));
            return;
        }

        String content = text.trim();
        if (content.isEmpty()) {
            sender.execute(sendContent.messageText(update, "❌正文不能为空，请重新输入。"));
            return;
        }
        if (content.length() > MAX_PROMPT_LENGTH) {
            sender.execute(sendContent.messageText(update, "❌内容过长（最多 " + MAX_PROMPT_LENGTH + " 字符），请精简后重新输入。"));
            return;
        }

        AiCsPrompt target = aiCsPromptService.selByIdAndGroupId(id, groupId);
        if (target == null) {
            aiCsCacheMap.clearFlag(userId);
            sender.execute(sendContent.messageText(update, "⚠️该条目已不存在（可能已被删除），请返回列表刷新。"));
            return;
        }
        if (aiCsPromptService.updatePrompt(id, groupId, content)) {
            aiCsCacheMap.clearFlag(userId);
            AiCsPrompt updated = aiCsPromptService.selByIdAndGroupId(id, groupId);
            sender.execute((SendMessage) sendContent.createResponseMessage(update, buildDetail(updated, id, "✅正文已更新"), "html"));
        } else {
            sender.execute(sendContent.messageText(update, "❌保存失败，请重试。"));
        }
    }

    /**
     * 组装条目详情页。
     *
     * @param prompt  条目；为 null 表示已被删除
     * @param id      条目主键（条目已删除时仍要用它渲染返回按钮）
     * @param tipLine 顶部的提示行（如「✅名称已更新」），可为 null
     */
    private KeywordsFormat buildDetail(AiCsPrompt prompt, Integer id, String tipLine) {
        List<String> buttons = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(tipLine)) {
            sb.append(tipLine).append("\n\n");
        }

        if (prompt == null) {
            sb.append("⚠️该条目已不存在（可能已被其他管理员删除）。");
            buttons.add("◀️返回列表##aiCsList%%❌关闭菜单##closeMenu");
        } else {
            String title = prompt.getTitle() == null ? "" : prompt.getTitle();
            String body = prompt.getPrompt() == null ? "" : prompt.getPrompt();
            sb.append("📄 <b>").append(escapeHtml(title)).append("</b>")
                    .append("\n编号：<code>").append(prompt.getId()).append("</code>")
                    .append("\n\n<b>正文</b>\n<pre>");

            boolean clipped = false;
            if (body.isEmpty()) {
                sb.append("（空）");
            } else {
                StringBuilder preview = new StringBuilder();
                // 按「码点」而不是 char 遍历：知识条目里常有 emoji（代理对），
                // 按 char 拆分会变成两个非法字符，Telegram 会直接拒收。
                int index = 0;
                while (index < body.length()) {
                    int codePoint = body.codePointAt(index);
                    index += Character.charCount(codePoint);
                    String escaped;
                    if (codePoint == '&') {
                        escaped = "&amp;";
                    } else if (codePoint == '<') {
                        escaped = "&lt;";
                    } else if (codePoint == '>') {
                        escaped = "&gt;";
                    } else {
                        escaped = new String(Character.toChars(codePoint));
                    }
                    if (preview.length() + escaped.length() > MAX_BODY_PREVIEW) {
                        clipped = true;
                        break;
                    }
                    preview.append(escaped);
                }
                sb.append(preview);
            }
            sb.append("</pre>");
            if (clipped) {
                sb.append("\n⚠️内容过长，仅显示前 ").append(MAX_BODY_PREVIEW)
                        .append(" 字符；点下方「✏️修改正文」可整段覆盖。");
            }

            buttons.add("✏️修改名称##aiCsEditTitle" + id);
            buttons.add("✏️修改正文##aiCsEditContent" + id);
            buttons.add("🗑删除此条目##aiCsDel" + id);
            buttons.add("◀️返回列表##aiCsList%%❌关闭菜单##closeMenu");
        }

        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText(sb.toString());
        format.setKeywordsButtons(buttons);
        return format;
    }

    /** 进入输入态前的前置检查：设置已超时则提示并阻止进入。 */
    private boolean rejectIfExpired(AbsSender sender, Update update, String userId, String groupId) throws TelegramApiException {
        if (groupId == null || !groupInfoService.isSettingExpired(groupId)) {
            return false;
        }
        aiCsCacheMap.clearFlag(userId);
        List<String> buttons = new ArrayList<>();
        buttons.add("◀️返回AI客服##aiCsMenu%%❌关闭菜单##closeMenu");
        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText("⏱ 本次设置已超时（有效期 15 分钟）。\n请在私聊里发送 /start 重新选择群组，即可继续操作。");
        format.setKeywordsButtons(buttons);
        sender.execute(sendContent.editResponseMessage(update, format, "html"));
        return true;
    }

    /** 从回调数据中取出主键，如 aiCsView12 → 12。 */
    private Integer parseCallbackId(Update update, String prefix) {
        if (update.getCallbackQuery() == null || update.getCallbackQuery().getData() == null) {
            return null;
        }
        return parseFlagId(update.getCallbackQuery().getData(), prefix);
    }

    /** 从 flag / 回调数据中取出主键，前缀不匹配或非数字时返回 null。 */
    private Integer parseFlagId(String data, String prefix) {
        if (data == null || !data.startsWith(prefix)) {
            return null;
        }
        try {
            return Integer.valueOf(data.substring(prefix.length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
