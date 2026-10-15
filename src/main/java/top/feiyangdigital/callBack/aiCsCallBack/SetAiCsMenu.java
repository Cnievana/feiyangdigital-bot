package top.feiyangdigital.callBack.aiCsCallBack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.sqlService.AiCsPromptService;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.SendContent;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;
import top.feiyangdigital.utils.ruleCacheMap.AiCsCacheMap;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 客服面板：菜单、总开关、触发方式、私聊开关。
 * <p>
 * 交互设计与「📝规则设置」保持一致：主菜单 → 添加/查看删除/开关，
 * 需要输入文本的操作通过 {@link AiCsCacheMap} 打标，再由 CommonFunction 分发。
 *
 * @author Cnievana
 */
@Component
public class SetAiCsMenu {

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

    /** 进入 AI 客服主菜单。 */
    public void hadleCallBack(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        sender.execute(sendContent.editResponseMessage(update, buildMenu(userId, null), "html"));
    }

    /** 切换「AI客服」总开关。 */
    public void toggleFlag(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        String current = groupInfoService.selAiCsFlag(groupId);
        String next = "open".equals(current) ? "close" : "open";
        String tip;
        if (groupInfoService.updateAiCsFlag(groupId, next)) {
            tip = "open".equals(next) ? "✅AI客服已开启" : "❗️AI客服已关闭";
        } else {
            tip = "❌操作失败，请重试";
        }
        sender.execute(sendContent.editResponseMessage(update, buildMenu(userId, tip), "html"));
    }

    /** 切换「私聊AI客服」开关。 */
    public void togglePrivate(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        String current = groupInfoService.selAiCsPrivateFlag(groupId);
        String next = "open".equals(current) ? "close" : "open";
        String tip;
        if (groupInfoService.updateAiCsPrivateFlag(groupId, next)) {
            tip = "open".equals(next) ? "✅私聊AI客服已开启" : "❗️私聊AI客服已关闭";
        } else {
            tip = "❌操作失败，请重试";
        }
        sender.execute(sendContent.editResponseMessage(update, buildMenu(userId, tip), "html"));
    }

    /** 群内触发方式菜单。 */
    public void triggerMenu(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        sender.execute(sendContent.editResponseMessage(update, buildTriggerMenu(userId, null), "html"));
    }

    /**
     * 设置群内触发方式。
     *
     * @param mode at / keyword / all
     */
    public void setTrigger(AbsSender sender, Update update, String mode) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        String tip;
        if (groupInfoService.updateAiCsTriggerMode(groupId, mode)) {
            if ("keyword".equals(mode)) {
                tip = "✅已切换为「命中关键词时回复」\n⚠️还需点击下方「✏️设置关键词」填写匹配规则";
            } else {
                tip = "✅已切换为「" + triggerCn(mode) + "」";
            }
        } else {
            tip = "❌操作失败，请重试";
        }
        sender.execute(sendContent.editResponseMessage(update, buildTriggerMenu(userId, tip), "html"));
    }

    /** 提示管理员输入关键词（进入 setKeyword 状态）。 */
    public void promptForKeyword(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        if (rejectIfExpired(sender, update, userId)) {
            return;
        }
        aiCsCacheMap.updateFlag(userId, "setKeyword");
        List<String> buttons = new ArrayList<>();
        buttons.add("◀️返回触发方式##aiCsTrigger");
        buttons.add("❌关闭菜单##closeMenu");
        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText("⚡️请直接在输入框里输入触发关键词。\n可填写<b>正则表达式</b>（例如：<code>价格|多少钱|怎么卖</code>），\n只要群成员发言命中即会唤起AI客服。\n\n⏱ 设置有效期 15 分钟，未超时期间每点一次按钮都会重新计时；一旦超时需回群里重新发送 /setbot。⚡️");
        format.setKeywordsButtons(buttons);
        sender.execute(sendContent.editResponseMessage(update, format, "html"));
    }

    /** 提示管理员输入知识条目（进入 addPrompt 状态）。 */
    public void addPrompt(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        if (rejectIfExpired(sender, update, userId)) {
            return;
        }
        aiCsCacheMap.updateFlag(userId, "addPrompt");
        List<String> buttons = new ArrayList<>();
        buttons.add("◀️返回AI客服##aiCsMenu%%📋查看所有条目##aiCsList");
        buttons.add("❌关闭菜单##closeMenu");
        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText("⚡️请直接在输入框里粘贴知识条目，格式如下：\n\n"
                + "<b>第一行</b>＝条目名称（用于面板区分，建议简短）\n"
                + "<b>第二行起</b>＝提示词正文（可多行）\n\n"
                + "例如：\n<code>退款政策\n本店支持7天无理由退款，请联系人工客服。</code>\n\n"
                + "若只输入一行，则整行既作名称也作正文。\n\n"
                + "⏱ 设置有效期 15 分钟，未超时期间每点一次按钮都会重新计时；一旦超时需回群里重新发送 /setbot。⚡️");
        format.setKeywordsButtons(buttons);
        sender.execute(sendContent.editResponseMessage(update, format, "html"));
    }

    /** 组装 AI 客服主菜单。 */
    public KeywordsFormat buildMenu(String userId, String tipLine) {
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        String aiCsFlag = groupInfoService.selAiCsFlag(groupId);
        String triggerMode = groupInfoService.selAiCsTriggerMode(groupId);
        String privateFlag = groupInfoService.selAiCsPrivateFlag(groupId);
        String keyword = groupInfoService.selAiCsKeyword(groupId);
        int count = aiCsPromptService.countByGroupId(groupId);

        List<String> buttons = new ArrayList<>();
        buttons.add("🔛客服总开关（" + cn(aiCsFlag) + "）##aiCsToggle");
        buttons.add("➕添加知识条目##aiCsAdd%%📋查看/删除条目##aiCsList");
        buttons.add("⚙️群内触发方式（" + triggerCn(triggerMode) + "）##aiCsTrigger");
        buttons.add("💬私聊客服（" + cn(privateFlag) + "）##aiCsPrivateToggle");
        buttons.add("◀️返回主菜单##backMainMenu%%❌关闭菜单##closeMenu");

        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(tipLine)) {
            sb.append(tipLine).append("\n");
        }
        sb.append("当前群组：<b>").append(addRuleCacheMap.getGroupNameForUser(userId)).append("</b>")
                .append("\n当前群组ID：<b>").append(groupId).append("</b>")
                .append("\n客服总开关：<b>").append(aiCsFlag).append("</b>")
                .append("\n群内触发方式：<b>").append(triggerMode).append("</b>");
        if ("keyword".equals(triggerMode)) {
            sb.append("（关键词：<code>").append(keyword == null || keyword.isEmpty() ? "未设置" : escapeHtml(keyword)).append("</code>）");
        }
        sb.append("\n私聊客服：<b>").append(privateFlag).append("</b>")
                .append("\n知识条目数：<b>").append(count).append("</b>")
                .append("\n⚡️请选择一个操作!⚡️");

        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText(sb.toString());
        format.setKeywordsButtons(buttons);
        return format;
    }

    /** 组装触发方式菜单。 */
    private KeywordsFormat buildTriggerMenu(String userId, String tipLine) {
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        String mode = groupInfoService.selAiCsTriggerMode(groupId);
        String keyword = groupInfoService.selAiCsKeyword(groupId);

        List<String> buttons = new ArrayList<>();
        buttons.add("🎯仅@Bot时回复" + mark("at", mode) + "##aiCsTriggerAt");
        buttons.add("🔑命中关键词时回复" + mark("keyword", mode) + "##aiCsTriggerKeyword");
        buttons.add("📢回复群内所有消息" + mark("all", mode) + "##aiCsTriggerAll");
        buttons.add("✏️设置关键词##aiCsSetKeyword");
        buttons.add("◀️返回AI客服##aiCsMenu%%❌关闭菜单##closeMenu");

        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(tipLine)) {
            sb.append(tipLine).append("\n");
        }
        sb.append("当前群组：<b>").append(addRuleCacheMap.getGroupNameForUser(userId)).append("</b>")
                .append("\n当前触发方式：<b>").append(mode).append("</b>")
                .append("\n当前关键词：<code>").append(keyword == null || keyword.isEmpty() ? "未设置" : escapeHtml(keyword)).append("</code>")
                .append("\n\n🎯<b>仅@Bot时回复</b>：最省额度，成员需 @ 机器人\n")
                .append("🔑<b>命中关键词时回复</b>：命中关键词才回复，需先设置关键词\n")
                .append("📢<b>回复群内所有消息</b>：⚠️消耗额度最快，慎用\n")
                .append("⚡️请选择一个操作!⚡️");

        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText(sb.toString());
        format.setKeywordsButtons(buttons);
        return format;
    }

    private String mark(String value, String current) {
        return value.equals(current) ? " ✅" : "";
    }

    /**
     * 进入输入状态前的前置检查：若设置已超时则提示并阻止进入输入态，
     * 避免管理员打完一整段提示词、提交时才发现会话已失效。
     *
     * @return true 表示已超时（调用方应直接 return）
     */
    private boolean rejectIfExpired(AbsSender sender, Update update, String userId) throws TelegramApiException {
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        if (groupId == null || !groupInfoService.isSettingExpired(groupId)) {
            return false;
        }
        aiCsCacheMap.clearFlag(userId);
        List<String> buttons = new ArrayList<>();
        buttons.add("◀️返回AI客服##aiCsMenu%%❌关闭菜单##closeMenu");
        KeywordsFormat format = new KeywordsFormat();
        format.setReplyText("⏱ 本次设置已超时（有效期 15 分钟）。\n超时后无法在面板继续操作，请回群里重新发送 <code>/setbot</code> 后重新进入面板。");
        format.setKeywordsButtons(buttons);
        sender.execute(sendContent.editResponseMessage(update, format, "html"));
        return true;
    }

    private String cn(String flag) {
        return "open".equals(flag) ? "已开启" : "已关闭";
    }

    private String triggerCn(String mode) {
        switch (mode) {
            case "keyword":
                return "命中关键词";
            case "all":
                return "所有消息";
            default:
                return "仅@Bot";
        }
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
