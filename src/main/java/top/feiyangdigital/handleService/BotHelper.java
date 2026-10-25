package top.feiyangdigital.handleService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.callBack.CommonCallBack;
import top.feiyangdigital.callBack.PanelMenu;
import top.feiyangdigital.callBack.aiCsCallBack.DeleteAiCsPrompt;
import top.feiyangdigital.callBack.aiCsCallBack.SetAiCsMenu;
import top.feiyangdigital.callBack.deleteRuleCallBack.SetDeleteView;
import top.feiyangdigital.callBack.groupSetting.SetGroupSettingView;
import top.feiyangdigital.callBack.otherGroupSetting.NightModeAndReport;
import top.feiyangdigital.callBack.otherGroupSetting.NightSetting;
import top.feiyangdigital.callBack.replyRuleCallBack.SetAutoReplyMenu;
import top.feiyangdigital.callBack.spamOption.AntiFlood;
import top.feiyangdigital.callBack.spamOption.SetFloodInfoCount;
import top.feiyangdigital.callBack.spamOption.SetFloodTime;
import top.feiyangdigital.entity.BaseInfo;
import top.feiyangdigital.entity.DeleteGropuRuleMapEntity;
import top.feiyangdigital.entity.GroupInfo;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.scheduledTasks.HandleOption;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.*;
import top.feiyangdigital.utils.groupCaptch.AdminAllow;
import top.feiyangdigital.utils.groupCaptch.CaptchaManagerCacheMap;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;
import top.feiyangdigital.utils.ruleCacheMap.DeleteRuleCacheMap;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BotHelper {

    @Autowired
    private NightSetting nightSetting;

    @Autowired
    private SetFloodTime setFloodTime;

    @Autowired
    private SetFloodInfoCount setFloodInfoCount;

    @Autowired
    private AntiFlood antiFlood;

    @Autowired
    private TimerDelete timerDelete;

    @Autowired
    private SetAutoReplyMenu setAutoReplyMenu;

    @Autowired
    private SetDeleteView setDeleteView;

    @Autowired
    private SendContent sendContent;

    @Autowired
    private CommonCallBack commonCallBack;

    @Autowired
    private PanelMenu panelMenu;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    @Autowired
    private DeleteRuleCacheMap deleteRuleCacheMap;

    @Autowired
    private SetAiCsMenu setAiCsMenu;

    @Autowired
    private DeleteAiCsPrompt deleteAiCsPrompt;

    @Autowired
    private DeleteGropuRuleMap deleteGropuRuleMap;

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private KeywordFileSender keywordFileSender;

    @Autowired
    private AdminAllow adminAllow;

    @Autowired
    private SetGroupSettingView setGroupSettingView;

    @Autowired
    private AdminList adminList;

    @Autowired
    private CaptchaManagerCacheMap captchaManagerCacheMap;

    @Autowired
    private HandleOption handleOption;

    @Autowired
    private NightModeAndReport nightModeAndReport;

    @Autowired
    private FollowChannelVerification followChannelVerificationl;

    public void sendAdminButton(AbsSender sender, Update update) {
        String url = String.format("https://t.me/%s?start=_groupId%s", BaseInfo.getBotName(), update.getMessage().getChatId().toString());
        List<String> keywordsButtons = new ArrayList<>();
        keywordsButtons.add("🤖Bot设置$$" + url);
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsFormat.setReplyText("点击跳转至群组设置：");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        timerDelete.sendTimedMessage(sender, (SendMessage) sendContent.createResponseMessage(update, keywordsFormat, "def"), 10);
    }


    /**
     * 展示/刷新私聊管理面板。
     * <p>
     * update 是私聊消息（/start 深链）时发新消息；是按钮回调（切换群组）时原地编辑。
     *
     * @param notice 面板顶部的提示语（可为 null）
     */
    public void showMainMenu(AbsSender sender, Update update, String userId, String notice) throws TelegramApiException {
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        boolean hasNotice = notice != null && !notice.isEmpty();
        keywordsFormat.setReplyText((hasNotice ? notice + "\n" : "") + panelMenu.mainMenuText(userId));
        keywordsFormat.setKeywordsButtons(panelMenu.mainMenuButtons());
        if (update.getCallbackQuery() != null) {
            sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
        } else {
            sender.execute((SendMessage) sendContent.createResponseMessage(update, keywordsFormat, "html"));
        }
    }

    /**
     * 让指定管理员进入指定群的管理面板（私聊）。
     * <p>
     * 两条入口共用：群内 /setbot 的深链（update 为 message）、私聊 /start 列表里选群（update 为 callbackQuery）。
     * <p>
     * 进入即把该群的「设置有效期」重置为 15 分钟 —— 以前只有群内 /setbot 能开启一次配置会话，
     * 现在私聊 /start 选群等价（这是私聊入口的核心变化）。
     *
     * @return 是否成功进入面板
     */
    public boolean openPanelForGroup(AbsSender sender, Update update, String userId, String groupId) throws TelegramApiException {
        GroupInfoWithBLOBs groupInfo = groupInfoService.selAllByGroupId(groupId);
        if (groupInfo == null) {
            sender.execute(sendContent.messageText(update, "❌该群组不存在或已被移除，请发送 /start 重新选择。"));
            return false;
        }
        if (!isManagedBy(groupInfo, userId)) {
            sender.execute(sendContent.messageText(update, "❌你无权管理该群组，仅群主或匿名管理员可操作。"));
            return false;
        }
        // 重置有效期 = 开启一次 15 分钟的配置会话
        GroupInfoWithBLOBs refresh = new GroupInfoWithBLOBs();
        refresh.setSettingtimestamp(String.valueOf(System.currentTimeMillis()));
        groupInfoService.updateSelectiveByChatId(refresh, groupId);
        // 绑定内存映射：面板所有按钮回调都靠这里找回“当前正在设置的群”
        addRuleCacheMap.updateUserMapping(userId, groupInfo.getGroupid(), groupInfo.getGroupname(), groupInfo.getKeywordsflag(), groupInfo.getAiflag(), groupInfo.getCrontabflag());
        deleteRuleCacheMap.updateUserMapping(userId, groupInfo.getGroupid(), groupInfo.getGroupname(), groupInfo.getDeletekeywordflag());
        showMainMenu(sender, update, userId, null);
        return true;
    }

    /**
     * 该群是否由该用户（群主 / 匿名管理员）管理。
     * ownerAndAnonymousAdmins 形如「123|456」，两端补「|」后再匹配，避免 userId=123 误命中 1234。
     */
    public static boolean isManagedBy(GroupInfoWithBLOBs groupInfo, String userId) {
        String admins = groupInfo.getOwnerandanonymousadmins();
        return admins != null && userId != null && ("|" + admins + "|").contains("|" + userId + "|");
    }

    /**
     * 展示「我管理的群组」列表，点选即可进入对应群的面板。
     * <p>
     * 这是私聊 /start 的落地页：不再需要先回群里发 /setbot。
     */
    public void sendGroupList(AbsSender sender, Update update) throws TelegramApiException {
        String userId = userIdOf(update);
        List<GroupInfo> groups = groupInfoService.selGroupsManagedBy(userId);

        List<String> keywordsButtons = new ArrayList<>();
        if (groups != null) {
            for (GroupInfo group : groups) {
                keywordsButtons.add(panelMenu.groupButtonText(group) + "##" + PanelMenu.SWITCH_GROUP + group.getGroupid());
            }
        }
        keywordsButtons.add("➕将Bot加入群组$$" + String.format("https://t.me/%s?startgroup=start", BaseInfo.getBotName()));
        keywordsButtons.add("❌关闭菜单##close");

        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsFormat.setReplyText(panelMenu.groupListText(groups == null ? 0 : groups.size()));
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        if (update.getCallbackQuery() != null) {
            sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
        } else {
            sender.execute((SendMessage) sendContent.createResponseMessage(update, keywordsFormat, "html"));
        }
    }

    /** 兼容从私聊消息 / 按钮回调两种 update 里取用户ID。 */
    public static String userIdOf(Update update) {
        if (update.getCallbackQuery() != null) {
            return update.getCallbackQuery().getFrom().getId().toString();
        }
        if (update.getMessage() != null) {
            return update.getMessage().getFrom().getId().toString();
        }
        return null;
    }

    /**
     * 面板按钮点击时刷新当前群组的「设置有效期」（settingTimeStamp）。
     * <p>
     * 规则：**仅在尚未超时时续期**；已超时则不续期，会话保持失效，
     * 需在私聊发送 /start 重新选择群组（或回群里重发 /setbot）才能重新开始配置。
     * <p>
     * 这里刻意分两次调用（读 → 写）而不是封装成一个 service 方法，
     * 是为了让 {@code @Cacheable} / {@code @Transactional} 都走代理，避免自调用导致注解失效。
     * <p>
     * 失败时只在日志记录，绝不影响面板本身的正常使用。
     */
    private void refreshSettingTimestamp(String userId) {
        try {
            String groupId = addRuleCacheMap.getGroupIdForUser(userId);
            if (groupId == null) {
                return;
            }
            if (GroupInfoService.isSettingExpired(groupInfoService.selAllByGroupId(groupId))) {
                // 已超时：不续期，需在私聊发送 /start 重新选择群组
                return;
            }
            GroupInfoWithBLOBs record = new GroupInfoWithBLOBs();
            record.setSettingtimestamp(String.valueOf(System.currentTimeMillis()));
            groupInfoService.updateSelectiveByChatId(record, groupId);
        } catch (Exception e) {
            log.warn("刷新设置有效期失败：{}", e.getMessage());
        }
    }

    public void handleCallbackQuery(AbsSender sender, Update update) throws TelegramApiException {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        if (callbackData == null || callbackData.isEmpty()) return;

        String userId = callbackQuery.getFrom().getId().toString();

        // 「切换群组」会重新绑定群组，旧绑定的有效期不续期（选中的新群在 openPanelForGroup 里统一重置）
        if (!callbackData.startsWith(PanelMenu.SWITCH_GROUP)) {
            // 面板按钮操作：未超时则重置有效期；已超时则不续期
            refreshSettingTimestamp(userId);
        }

        // 「切换群组」：无群ID → 回到群组列表；带群ID → 进入该群面板
        if (callbackData.startsWith(PanelMenu.SWITCH_GROUP)) {
            // 静默应答：让客户端按钮立即停止 loading（纯切换操作，无需弹提示）
            AnswerCallbackQuery switchAnswer = new AnswerCallbackQuery();
            switchAnswer.setCallbackQueryId(callbackQuery.getId());
            sender.execute(switchAnswer);
            String targetGroupId = callbackData.substring(PanelMenu.SWITCH_GROUP.length());
            if (targetGroupId.isEmpty()) {
                sendGroupList(sender, update);
            } else {
                openPanelForGroup(sender, update, userId, targetGroupId);
            }
            return;
        }

        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        switch (callbackData) {
            case "changeGroupCheckFlag":
                setGroupSettingView.changeGroupCheckFlag(sender, update);
                return;
            case "changeToJoinChannel":
                setGroupSettingView.changeToJoinChannel(sender, update);
                return;
            case "changeToCompute":
                setGroupSettingView.changeToCompute(sender, update);
                return;
            case "answerReplyhandle":
                followChannelVerificationl.answerReplyhandle(sender, update);
                return;
            case "openCanSendMediaFlag":
                nightSetting.openCanSendMediaFlag(sender, update);
                return;
            case "closeCanSendMediaFlag":
                nightSetting.closeCanSendMediaFlag(sender, update);
                return;
            case "nightSetting":
                nightSetting.hadleCallBack(sender, update);
                return;
            case "spamChannelBot":
                nightModeAndReport.spamChannelBot(sender, update);
                return;
            case "setFloodTime":
                setFloodTime.haddle(sender, update, false);
                return;
            case "setFloodInfoCount":
                setFloodInfoCount.haddle(sender, update, false);
                return;
            case "closeAntiFloodFlag":
                antiFlood.closeAntiFloodFlag(sender, update);
                return;
            case "openAntiFloodFlag":
                antiFlood.openAntiFloodFlag(sender, update);
                return;
            case "antiFlood":
                antiFlood.hadleCallBack(sender, update);
                return;
            case "changeIntoGroupUserNameCheckStatus":
                setGroupSettingView.changeIntoGroupUserNameCheckStatus(sender, update);
                return;
            case "changeIntoGroupAiCheckStatus":
                setGroupSettingView.changeIntoGroupAiCheckStatus(sender, update);
                return;
            case "changeGroupWelcomeStatus":
                setGroupSettingView.changeGroupWelcomeStatus(sender, update);
                return;
            case "autoReply":
                setAutoReplyMenu.hadleCallBack(sender, update);
                return;
            case "groupSetting":
                setGroupSettingView.hadleCallBack(sender, update);
                return;
            case "changeGroupCheckStatus":
                setGroupSettingView.changeGroupCheckStatus(sender, update);
                return;
            case "addReplyRule":
                setAutoReplyMenu.addReplyRule(sender, update);
                return;
            case "selAllReplyRules":
                keywordFileSender.sendKeywordsFile(sender, update);
                return;
            case "selAndDeleteReplyRule":
                setDeleteView.deleteRuleView(sender, update);
                return;
            case "backToAutoReply":
                setAutoReplyMenu.backToAutoReply(sender, update);
                return;
            case "deleteBackToAutoReply":
                setDeleteView.deleteBackToAutoReply(sender, update);
                return;
            case "backMainMenu":
                commonCallBack.backMainMenu(sender, update);
                return;
            case "closeMenu":
                timerDelete.deletePrivateMessageImmediately(sender, update);
                return;
            case "aiOption":
                commonCallBack.aiOption(sender, update);
                return;
            case "aiMuteOption":
                commonCallBack.aiMuteOption(sender, update);
                return;
            case "aiCsMenu":
                setAiCsMenu.hadleCallBack(sender, update);
                return;
            case "aiCsToggle":
                setAiCsMenu.toggleFlag(sender, update);
                return;
            case "aiCsPrivateToggle":
                setAiCsMenu.togglePrivate(sender, update);
                return;
            case "aiCsTrigger":
                setAiCsMenu.triggerMenu(sender, update);
                return;
            case "aiCsTriggerAt":
                setAiCsMenu.setTrigger(sender, update, "at");
                return;
            case "aiCsTriggerKeyword":
                setAiCsMenu.setTrigger(sender, update, "keyword");
                return;
            case "aiCsTriggerAll":
                setAiCsMenu.setTrigger(sender, update, "all");
                return;
            case "aiCsSetKeyword":
                setAiCsMenu.promptForKeyword(sender, update);
                return;
            case "aiCsAdd":
                setAiCsMenu.addPrompt(sender, update);
                return;
            case "aiCsList":
                deleteAiCsPrompt.listPrompts(sender, update);
                return;
            case "cronOption":
                commonCallBack.cronOption(sender, update);
                return;
            case "otherGroupSetting":
                nightModeAndReport.hadleCallBack(sender, update);
                return;
            case "changeNightModeFlag":
                nightSetting.changeNightModeFlag(sender, update);
                return;
            case "reportToAdmin":
                nightModeAndReport.reportToAdmin(sender, update);
                return;
            case "clearCommand":
                nightModeAndReport.clearCommand(sender, update);
                return;
            case "close":
                timerDelete.deletePrivateUsualMessageImmediately(sender, update);
                return;
            default:
        }

        // AI 客服：删除知识条目（回调数据形如 aiCsDel12）
        if (callbackData.startsWith("aiCsDel")) {
            deleteAiCsPrompt.deleteById(sender, update);
            return;
        }

        if (callbackData.startsWith("adminUnrestrict")) {

            for (ChatMember admin : adminList.getAdmins(sender, callbackQuery.getMessage().getChatId().toString())) {
                if ("GroupAnonymousBot".equals(callbackQuery.getFrom().getUserName()) || admin.getUser().getId().equals(callbackQuery.getFrom().getId())) {
                    adminAllow.allow(sender, Long.valueOf(callbackData.substring(15)), callbackQuery.getMessage().getChatId().toString(), captchaManagerCacheMap.getMessageIdForUser(callbackData.substring(15), callbackQuery.getMessage().getChatId().toString()), answer, true);
                    return;
                }
            }
            answer.setText("❌你无权执行该操作！");
            sender.execute(answer);
            return;
        }

        if (callbackData.startsWith("adminUnBan")) {
            for (ChatMember admin : adminList.getAdmins(sender, callbackQuery.getMessage().getChatId().toString())) {
                if ("GroupAnonymousBot".equals(callbackQuery.getFrom().getUserName()) || admin.getUser().getId().equals(callbackQuery.getFrom().getId())) {
                    adminAllow.allowUnBan(sender, Long.valueOf(callbackData.substring(10)), callbackQuery.getMessage().getChatId().toString(), captchaManagerCacheMap.getMessageIdForUser(callbackData.substring(10), callbackQuery.getMessage().getChatId().toString()), answer);
                    return;
                }
            }
            answer.setText("❌你无权执行该操作！");
            sender.execute(answer);
            return;
        }

        if (callbackData.startsWith("adminUnmute")) {
            for (ChatMember admin : adminList.getAdmins(sender, callbackQuery.getMessage().getChatId().toString())) {
                if ("GroupAnonymousBot".equals(callbackQuery.getFrom().getUserName()) || admin.getUser().getId().equals(callbackQuery.getFrom().getId())) {
                    adminAllow.allow(sender, Long.valueOf(callbackData.substring(11)), callbackQuery.getMessage().getChatId().toString(), captchaManagerCacheMap.getMessageIdForUser(callbackData.substring(11), callbackQuery.getMessage().getChatId().toString()), answer, false);
                    return;
                }
            }
            answer.setText("❌你无权执行该操作！");
            sender.execute(answer);
            return;
        }

        if (callbackData.startsWith("floodInfoCount")) {
            setFloodInfoCount.haddle(sender, update, true);
        }

        if (callbackData.startsWith("floodTime")) {
            setFloodTime.haddle(sender, update, true);
        }

        if (deleteGropuRuleMap.getGroupRuleMapSize() > 0) {
            String chatId = deleteRuleCacheMap.getGroupIdForUser(update.getCallbackQuery().getFrom().getId().toString());
            String longUuid = deleteGropuRuleMap.getAllRulesFromGroupId(chatId).getShortUuidToFullUuidMap().get(callbackData);
            if (longUuid != null && callbackData.equals(longUuid.substring(0, 5))) {
                DeleteGropuRuleMapEntity deleteGropuRuleMapEntity = new DeleteGropuRuleMapEntity(deleteGropuRuleMap);
                GroupInfoWithBLOBs groupInfoWithBLOBs = new GroupInfoWithBLOBs();
                groupInfoWithBLOBs.setKeywords(deleteGropuRuleMapEntity.removeRuleAndAssembleString(chatId, longUuid).trim());
                if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs, chatId)) {
                    GroupInfoWithBLOBs groupInfoWithBLOBs2 = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(update.getCallbackQuery().getFrom().getId().toString()));
                    String groupName = groupInfoWithBLOBs2.getGroupname();
                    String keyWords = groupInfoWithBLOBs2.getKeywords();
                    if (StringUtils.hasText(keyWords)) {
                        handleOption.ruleHandle(sender, addRuleCacheMap.getGroupIdForUser(update.getCallbackQuery().getFrom().getId().toString()), groupName, keyWords);
                    }
                    setDeleteView.deleteRuleSuccessCallBack(sender, update);
                }

            }
        }
    }

}
