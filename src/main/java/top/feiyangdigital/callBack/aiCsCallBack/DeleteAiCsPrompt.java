package top.feiyangdigital.callBack.aiCsCallBack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.feiyangdigital.entity.AiCsPrompt;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.sqlService.AiCsPromptService;
import top.feiyangdigital.utils.SendContent;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 客服：知识条目列表与删除。
 * <p>
 * 回调数据格式：{@code aiCsDel<id>}（id 为 ai_cs_prompt 主键）。
 * 与「规则删除」不同，这里不注册内存短码映射，直接用主键，简单且不会冲突。
 *
 * @author Cnievana
 */
@Component
public class DeleteAiCsPrompt {

    /** 面板上单个按钮文本的最大长度 */
    private static final int MAX_BUTTON_TEXT = 28;

    @Autowired
    private SendContent sendContent;

    @Autowired
    private AiCsPromptService aiCsPromptService;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    /** 列出当前群的全部知识条目。 */
    public void listPrompts(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        sender.execute(sendContent.editResponseMessage(update, buildListFormat(groupId, null), "html"));
    }

    /** 点击条目按钮后删除。 */
    public void deleteById(AbsSender sender, Update update) throws TelegramApiException {
        String callbackData = update.getCallbackQuery().getData();
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);

        Integer id;
        try {
            id = Integer.valueOf(callbackData.substring("aiCsDel".length()));
        } catch (NumberFormatException e) {
            return;
        }

        AiCsPrompt target = aiCsPromptService.selByIdAndGroupId(id, groupId);
        String tip;
        if (target == null) {
            tip = "⚠️该条目已不存在，请刷新列表";
        } else if (aiCsPromptService.delete(id, groupId)) {
            tip = "✅已删除条目：<b>" + escapeHtml(target.getTitle()) + "</b>";
        } else {
            tip = "❌删除失败，请重试";
        }
        sender.execute(sendContent.editResponseMessage(update, buildListFormat(groupId, tip), "html"));
    }

    private KeywordsFormat buildListFormat(String groupId, String tipLine) {
        List<AiCsPrompt> list = aiCsPromptService.selByGroupId(groupId);

        List<String> buttons = new ArrayList<>();
        if (list.isEmpty()) {
            buttons.add("（暂无知识条目）##aiCsList");
        } else {
            for (AiCsPrompt prompt : list) {
                String label = prompt.getTitle() == null || prompt.getTitle().isEmpty()
                        ? ("#" + prompt.getId())
                        : prompt.getTitle();
                label = sanitizeButtonText(label);
                buttons.add("🗑 " + label + "##aiCsDel" + prompt.getId());
            }
        }
        buttons.add("➕添加知识条目##aiCsAdd");
        buttons.add("◀️返回AI客服##aiCsMenu%%❌关闭菜单##closeMenu");

        StringBuilder sb = new StringBuilder();
        if (tipLine != null && !tipLine.isEmpty()) {
            sb.append(tipLine).append("\n");
        }
        sb.append("当前群组ID：<b>").append(groupId).append("</b>")
                .append("\n知识条目数：<b>").append(list.size()).append("</b>")
                .append("\n\n⚡️点击下方按钮即可删除对应条目（删除后立即生效）⚡️");

        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText(sb.toString());
        format.setKeywordsButtons(buttons);
        return format;
    }

    /** 清洗按钮文本：去掉会破坏回调解析的符号，并限制长度。 */
    private String sanitizeButtonText(String text) {
        String cleaned = text.replace("\n", " ").replace("##", "#").replace("$$", "$").trim();
        if (cleaned.isEmpty()) {
            cleaned = "未命名";
        }
        if (cleaned.length() > MAX_BUTTON_TEXT) {
            cleaned = cleaned.substring(0, MAX_BUTTON_TEXT) + "…";
        }
        return cleaned;
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
