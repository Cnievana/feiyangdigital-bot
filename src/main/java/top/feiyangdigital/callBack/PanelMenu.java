package top.feiyangdigital.callBack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.feiyangdigital.entity.GroupInfo;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;

import java.util.ArrayList;
import java.util.List;

/**
 * 私聊管理面板的「按钮 + 文案」唯一定义处。
 * <p>
 * 背景：主菜单按钮原来分散在 4 个方法里重复硬编码（BotHelper.sendInlineKeyboard
 * 与 CommonCallBack.cronOption / aiOption / backMainMenu），新增一个按钮必须同步 4 处，
 * 极易漏改。现统一收敛到本类，{@link top.feiyangdigital.handleService.BotHelper}
 * 与 {@link CommonCallBack} 都复用这里的方法。
 */
@Component
public class PanelMenu {

    /**
     * 「切换群组」按钮的回调前缀。
     * <p>
     * 不带群ID（{@code switchGroup}）表示回到群组列表；带群ID（{@code switchGroup<groupId>}）
     * 表示进入该群的管理面板。
     */
    public static final String SWITCH_GROUP = "switchGroup";

    /** 群名在按钮上展示的最大长度（超出截断），避免按钮文本过长被 Telegram 裁掉。 */
    private static final int MAX_NAME_LENGTH = 22;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    @Autowired
    private GroupInfoService groupInfoService;

    /**
     * 面板主菜单按钮（唯一定义处，新增按钮只改这里）。
     */
    public List<String> mainMenuButtons() {
        List<String> buttons = new ArrayList<>();
        buttons.add("📝规则设置##autoReply%%⚙️群组设置##groupSetting");
        buttons.add("🕐打开/关闭定时发送消息##cronOption%%🔮打开/关闭AI##aiOption");
        buttons.add("🌊防刷屏模式##antiFlood%%💎其他群组设置##otherGroupSetting");
        buttons.add("⛔AI违规自动禁言##aiMuteOption%%☎️AI智能客服##aiCsMenu");
        buttons.add("⇆切换群组##" + SWITCH_GROUP);
        buttons.add("仓库地址$$https://github.com/Cnievana/feiyangdigital-bot%%官方群组$$https://t.me/stonehd");
        buttons.add("❌关闭菜单##closeMenu");
        return buttons;
    }

    /**
     * 面板顶部的群组状态文本（当前群组 / 群ID / 各开关状态 / 设置有效期）。
     */
    public String mainMenuText(String userId) {
        String groupId = addRuleCacheMap.getGroupIdForUser(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("当前群组：<b>").append(escapeHtml(addRuleCacheMap.getGroupNameForUser(userId))).append("</b>\n");
        sb.append("当前群组ID：<b>").append(escapeHtml(groupId)).append("</b>\n");
        sb.append("当前可输入状态：<b>").append(escapeHtml(addRuleCacheMap.getKeywordsFlagForUser(userId))).append("</b>\n");
        sb.append("当前定时发送消息状态：<b>").append(escapeHtml(addRuleCacheMap.getCrontabFlagForUser(userId))).append("</b>\n");
        sb.append("当前AI状态：<b>").append(escapeHtml(addRuleCacheMap.getAiFlagForUser(userId))).append("</b>\n");
        sb.append("当前AI违规自动禁言：<b>").append(escapeHtml(groupInfoService.selAiMuteFlag(groupId))).append("</b>\n");
        sb.append(settingExpireText(groupId));
        sb.append("⚡️请选择一个操作!⚡️");
        return sb.toString();
    }

    /**
     * 「设置有效期」的剩余时间提示。
     * <p>
     * 仅用于展示，判定逻辑仍然只有一处：{@link GroupInfoService#isSettingExpired(GroupInfoWithBLOBs)}。
     * 时间戳为空/格式异常时（与判定逻辑一致，视为未超时）不展示倒计时，避免误导。
     */
    public String settingExpireText(String groupId) {
        try {
            GroupInfoWithBLOBs groupInfo = groupInfoService.selAllByGroupId(groupId);
            if (groupInfo == null) {
                return "";
            }
            if (GroupInfoService.isSettingExpired(groupInfo)) {
                return "⏱ 设置有效期：<b>已超时</b>，请发送 /start 重新选择群组\n";
            }
            long elapsed = System.currentTimeMillis() - Long.parseLong(groupInfo.getSettingtimestamp());
            long leftMinutes = Math.max(1, (GroupInfoService.SETTING_TIMEOUT_MS - elapsed) / 60000);
            return "⏱ 设置有效期：剩余约 <b>" + leftMinutes + "</b> 分钟（每次操作自动续期）\n";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 群组列表页的正文。
     */
    public String groupListText(int groupCount) {
        if (groupCount == 0) {
            return "<b>你还没有可管理的群组</b>\n请先把 Bot 拉进群，并在群内发送 <code>/setbot</code> 完成初始化；\n初始化后即可直接在私聊里进入该群的管理面板。\n";
        }
        return "<b>请选择要管理的群组（共 " + groupCount + " 个）：</b>\n点选即可进入该群的管理面板（有效期 15 分钟，操作时自动续期）。\n";
    }

    /**
     * 群组列表页的单个群按钮文本：群名（群ID），便于区分同名群。
     */
    public String groupButtonText(GroupInfo group) {
        return safeName(group.getGroupname()) + "（" + group.getGroupid() + "）";
    }

    /**
     * 按钮文本安全处理：源码里用 {@code %%} 分行、{@code ##} 分「文本/回调」、{@code $$} 分「文本/链接」，
     * 群名若含这些串会把一个按钮拆成多个，必须先清理；同时去掉换行与超长截断。
     */
    public static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "未命名群组";
        }
        String text = name.replace("%%", " ").replace("##", " ").replace("$$", " ");
        text = text.replaceAll("[\\r\\n\\t]", " ").trim();
        if (text.isEmpty()) {
            return "未命名群组";
        }
        if (text.length() > MAX_NAME_LENGTH) {
            return text.substring(0, MAX_NAME_LENGTH - 1) + "…";
        }
        return text;
    }

    /**
     * HTML 转义：面板以 {@code parseMode=html} 发送，群名等动态内容含 {@code < & >} 会导致发送失败。
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
