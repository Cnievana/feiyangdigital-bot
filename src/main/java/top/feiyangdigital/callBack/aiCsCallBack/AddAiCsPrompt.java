package top.feiyangdigital.callBack.aiCsCallBack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * AI 客服：处理管理员在私聊中输入的知识条目与触发关键词。
 *
 * @author Cnievana
 */
@Component
public class AddAiCsPrompt {

    /** 提示词正文长度上限（Telegram 单条消息上限 4096，留出余量） */
    private static final int MAX_PROMPT_LENGTH = 3800;
    /** 条目名称长度上限（与库表 varchar(100) 对齐，实际按 50 截断） */
    private static final int MAX_TITLE_LENGTH = 50;
    /** 关键词长度上限（与库表 varchar(500) 对齐） */
    private static final int MAX_KEYWORD_LENGTH = 500;

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

    @Autowired
    private SetAiCsMenu setAiCsMenu;

    /** 处理管理员输入的知识条目（flag = addPrompt）。 */
    public void addNewPrompt(AbsSender sender, Update update) throws TelegramApiException {
        String text = update.getMessage().getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String userId = update.getMessage().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        if (groupId == null) {
            return;
        }
        if (!"addPrompt".equals(aiCsCacheMap.getFlagForUser(userId))) {
            return;
        }
        if (groupInfoService.isSettingExpired(groupId)) {
            aiCsCacheMap.clearFlag(userId);
            sender.execute(sendContent.messageText(update, "⏱ 本次设置已超时（有效期 15 分钟）。\n超时后无法在面板续期，请回群里重新发送 /setbot 后重新进入面板。"));
            return;
        }

        String trimmed = text.trim();
        if (trimmed.length() > MAX_PROMPT_LENGTH) {
            sender.execute(sendContent.messageText(update, "❌内容过长（最多 " + MAX_PROMPT_LENGTH + " 字符），请精简后重新输入。"));
            return;
        }

        // 第一行为条目名称，其余为正文；只有一行时整行既作名称也作正文
        String title;
        String prompt;
        int newlineIndex = trimmed.indexOf('\n');
        if (newlineIndex > 0) {
            title = trimmed.substring(0, newlineIndex).trim();
            prompt = trimmed.substring(newlineIndex + 1).trim();
        } else {
            title = "";
            prompt = trimmed;
        }
        if (prompt.isEmpty()) {
            prompt = trimmed;
        }
        if (title.isEmpty()) {
            title = prompt.length() > 20 ? prompt.substring(0, 20) : prompt;
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        AiCsPrompt record = new AiCsPrompt();
        record.setGroupid(groupId);
        record.setTitle(title);
        record.setPrompt(prompt);
        record.setSortorder(aiCsPromptService.countByGroupId(groupId));
        record.setUpdatetimestamp(String.valueOf(System.currentTimeMillis()));

        if (aiCsPromptService.add(record)) {
            aiCsCacheMap.clearFlag(userId);
            String tip = "✅知识条目已添加：<b>" + escapeHtml(title) + "</b>";
            sender.execute((SendMessage) sendContent.createResponseMessage(update, setAiCsMenu.buildMenu(userId, tip), "html"));
        } else {
            sender.execute(sendContent.messageText(update, "❌添加失败，请重试。"));
        }
    }

    /** 处理管理员输入的关键词（flag = setKeyword）。 */
    public void setKeyword(AbsSender sender, Update update) throws TelegramApiException {
        String text = update.getMessage().getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String userId = update.getMessage().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        if (groupId == null) {
            return;
        }
        if (!"setKeyword".equals(aiCsCacheMap.getFlagForUser(userId))) {
            return;
        }
        if (groupInfoService.isSettingExpired(groupId)) {
            aiCsCacheMap.clearFlag(userId);
            sender.execute(sendContent.messageText(update, "⏱ 本次设置已超时（有效期 15 分钟）。\n超时后无法在面板续期，请回群里重新发送 /setbot 后重新进入面板。"));
            return;
        }

        String keyword = text.trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            sender.execute(sendContent.messageText(update, "❌关键词过长（最多 " + MAX_KEYWORD_LENGTH + " 字符）。"));
            return;
        }
        try {
            Pattern.compile(keyword);
        } catch (PatternSyntaxException e) {
            sender.execute(sendContent.messageText(update, "❌关键词不是合法的正则表达式，请重新输入：\n" + e.getDescription()));
            return;
        }

        if (groupInfoService.updateAiCsKeyword(groupId, keyword)) {
            aiCsCacheMap.clearFlag(userId);
            List<String> buttons = new ArrayList<>();
            buttons.add("◀️返回AI客服##aiCsMenu");
            buttons.add("❌关闭菜单##closeMenu");
            KeywordsFormat format = new KeywordsFormat();
            format.setReplyText("✅关键词已保存：<code>" + escapeHtml(keyword) + "</code>\n⚡️群内发言命中该正则即会唤起AI客服⚡️");
            format.setKeywordsButtons(buttons);
            sender.execute((SendMessage) sendContent.createResponseMessage(update, format, "html"));
        } else {
            sender.execute(sendContent.messageText(update, "❌保存失败，请重试。"));
        }
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
