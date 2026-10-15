package top.feiyangdigital.callBack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.scheduledTasks.HandleOption;
import top.feiyangdigital.scheduledTasks.SchedulerService;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;
import top.feiyangdigital.utils.SendContent;

import java.util.ArrayList;
import java.util.List;

@Component
public class CommonCallBack {

    @Autowired
    private SendContent sendContent;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private HandleOption handleOption;


    public void cronOption(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        String groupName = groupInfoWithBLOBs.getGroupname();
        String keyWords = groupInfoWithBLOBs.getKeywords();
        String crontabFlag = "";
        String text = "";
        GroupInfoWithBLOBs groupInfoWithBLOBs1 = new GroupInfoWithBLOBs();
        if ("close".equals(groupInfoWithBLOBs.getCrontabflag())) {
            groupInfoWithBLOBs1.setCrontabflag("open");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                crontabFlag = "open";
                text = "✅定时发送消息已打开";
                if (StringUtils.hasText(keyWords)) {
                    handleOption.ruleHandle(sender, addRuleCacheMap.getGroupIdForUser(userId), groupName, keyWords);
                }
            }
        } else {
            groupInfoWithBLOBs1.setCrontabflag("close");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                crontabFlag = "close";
                text = "❗定时发送消息已关闭";
                schedulerService.clearJobsWithGroupPrefix("OnlySendMessagejob_" + groupInfoWithBLOBs.getGroupid());
            }
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("📝规则设置##autoReply%%⚙️群组设置##groupSetting");
        keywordsButtons.add("🕐打开/关闭定时发送消息##cronOption%%🔮打开/关闭AI##aiOption");
        keywordsButtons.add("🌊防刷屏模式##antiFlood%%💎其他群组设置##otherGroupSetting");
        keywordsButtons.add("☎️AI智能客服##aiCsMenu");
        keywordsButtons.add("仓库地址$$https://github.com/Cnievana/feiyangdigital-bot%%官方群组$$https://t.me/stonehd");
        keywordsButtons.add("❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前可输入状态：<b>" + addRuleCacheMap.getKeywordsFlagForUser(userId) + "</b>\n当前定时发送消息状态：<b>" + crontabFlag + "</b>\n当前AI状态：<b>" + addRuleCacheMap.getAiFlagForUser(userId) + "</b>\n⚡️请选择一个操作!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        addRuleCacheMap.updateUserMapping(userId, addRuleCacheMap.getGroupIdForUser(userId), addRuleCacheMap.getGroupNameForUser(userId), addRuleCacheMap.getKeywordsFlagForUser(userId), addRuleCacheMap.getAiFlagForUser(userId), crontabFlag);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));

    }


    public void aiOption(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        String aiFlag = "";
        String text = "";
        GroupInfoWithBLOBs groupInfoWithBLOBs1 = new GroupInfoWithBLOBs();
        if ("close".equals(groupInfoWithBLOBs.getAiflag())) {
            groupInfoWithBLOBs1.setAiflag("open");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                aiFlag = "open";
                text = "✅智能AI监控已打开";
            }
        } else {
            groupInfoWithBLOBs1.setAiflag("close");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                aiFlag = "close";
                text = "❗智能AI监控已关闭";
            }
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("📝规则设置##autoReply%%⚙️群组设置##groupSetting");
        keywordsButtons.add("🕐打开/关闭定时发送消息##cronOption%%🔮打开/关闭AI##aiOption");
        keywordsButtons.add("🌊防刷屏模式##antiFlood%%💎其他群组设置##otherGroupSetting");
        keywordsButtons.add("☎️AI智能客服##aiCsMenu");
        keywordsButtons.add("仓库地址$$https://github.com/Cnievana/feiyangdigital-bot%%官方群组$$https://t.me/stonehd");
        keywordsButtons.add("❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前可输入状态：<b>" + addRuleCacheMap.getKeywordsFlagForUser(userId) + "</b>\n当前定时发送消息状态：<b>" + addRuleCacheMap.getCrontabFlagForUser(userId) + "</b>\n当前AI状态：<b>" + aiFlag + "</b>\n⚡️请选择一个操作!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        addRuleCacheMap.updateUserMapping(userId, addRuleCacheMap.getGroupIdForUser(userId), addRuleCacheMap.getGroupNameForUser(userId), addRuleCacheMap.getKeywordsFlagForUser(userId), aiFlag, addRuleCacheMap.getCrontabFlagForUser(userId));
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

    public void backMainMenu(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
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
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }
}
