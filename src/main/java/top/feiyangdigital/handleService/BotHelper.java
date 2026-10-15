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


    public void sendInlineKeyboard(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getMessage().getFrom().getId().toString();

        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("📝规则设置##autoReply%%⚙️群组设置##groupSetting");
        keywordsButtons.add("🕐打开/关闭定时发送消息##cronOption%%🔮打开/关闭AI##aiOption");
        keywordsButtons.add("🌊防刷屏模式##antiFlood%%💎其他群组设置##otherGroupSetting");
        keywordsButtons.add("☎️AI智能客服##aiCsMenu");
        keywordsButtons.add("仓库地址$$https://github.com/Cnievana/feiyangdigital-bot%%官方群组$$https://t.me/stonehd");
        keywordsButtons.add("❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText("当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前可输入状态：<b>" + addRuleCacheMap.getKeywordsFlagForUser(userId) + "</b>\n当前定时发送消息状态：<b>" + addRuleCacheMap.getCrontabFlagForUser(userId) + "</b>\n当前AI状态：<b>" + addRuleCacheMap.getAiFlagForUser(userId) + "</b>\n⚡️请选择一个操作!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute((SendMessage) sendContent.createResponseMessage(update, keywordsFormat, "html"));
    }

    /**
     * 面板按钮点击时刷新当前群组的「设置有效期」（settingTimeStamp）。
     * <p>
     * 规则：**仅在尚未超时时续期**；已超时则不续期，会话保持失效，
     * 必须回群里重新发送 /setbot 才能重新开始配置。
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
                // 已超时：不续期，必须回群里重新发送 /setbot
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

        // 面板按钮操作：未超时则重置有效期；已超时则不续期（需回群里重新发送 /setbot）
        refreshSettingTimestamp(callbackQuery.getFrom().getId().toString());

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
