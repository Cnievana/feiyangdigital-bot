package top.feiyangdigital.callBack.groupSetting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.SendContent;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;

import java.util.ArrayList;
import java.util.List;

@Component
public class SetGroupSettingView {

    @Autowired
    private SendContent sendContent;

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    public void hadleCallBack(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("🔑进群验证设置##changeGroupCheckStatus%%🎉是/否设置进群欢迎词##changeGroupWelcomeStatus");
        keywordsButtons.add("🔍是/否进群昵称违规检测##changeIntoGroupUserNameCheckStatus%%🤖是/否AI进群昵称/简介检测##changeIntoGroupAiCheckStatus");
        keywordsButtons.add("◀️返回主菜单##backMainMenu%%❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText("当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前群组验证状态：<b>" + groupInfoWithBLOBs.getIntogroupcheckflag() + "</b>\n当前进群欢迎状态：<b>" + groupInfoWithBLOBs.getIntogroupwelcomeflag() + "</b>\n当前进群昵称验证状态：<b>" + groupInfoWithBLOBs.getIntogroupusernamecheckflag() + "</b>\n当前AI进群昵称/简介检测状态：<b>" + groupInfoService.selIntoGroupAiCheckFlag(addRuleCacheMap.getGroupIdForUser(userId)) + "</b>\n⚡️请选择一个操作!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

    public void changeIntoGroupUserNameCheckStatus(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        String intoGroupUserNameCheck = "";
        String text = "";
        GroupInfoWithBLOBs groupInfoWithBLOBs1 = new GroupInfoWithBLOBs();
        if ("close".equals(groupInfoWithBLOBs.getIntogroupusernamecheckflag())) {
            groupInfoWithBLOBs1.setIntogroupusernamecheckflag("open");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                intoGroupUserNameCheck = "open";
                text = "✅进群昵称检测功能已打开";
            }
        } else {
            groupInfoWithBLOBs1.setIntogroupusernamecheckflag("close");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                intoGroupUserNameCheck = "close";
                text = "❗️进群昵称检测功能已关闭";
            }
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("🔑进群验证设置##changeGroupCheckStatus%%🎉设置进群欢迎词##changeGroupWelcomeStatus");
        keywordsButtons.add("🔍进群昵称违规检测##changeIntoGroupUserNameCheckStatus%%🤖是/否AI进群昵称/简介检测##changeIntoGroupAiCheckStatus");
        keywordsButtons.add("◀️返回主菜单##backMainMenu%%❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前群组验证状态：<b>" + groupInfoWithBLOBs.getIntogroupcheckflag() + "</b>\n当前进群欢迎状态：<b>" + groupInfoWithBLOBs.getIntogroupwelcomeflag() + "</b>\n当前进群昵称验证状态：<b>" + intoGroupUserNameCheck + "</b>\n当前AI进群昵称/简介检测状态：<b>" + groupInfoService.selIntoGroupAiCheckFlag(addRuleCacheMap.getGroupIdForUser(userId)) + "</b>\n⚡️请选择一个操作!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

    /**
     * 切换「AI进群昵称/简介检测」开关。
     * 打开后，新成员入群时会用 AI 判定其昵称与个人简介，判定为广告则直接永久限制、跳过验证。
     */
    public void changeIntoGroupAiCheckStatus(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        String chatId = addRuleCacheMap.getGroupIdForUser(userId);
        String current = groupInfoService.selIntoGroupAiCheckFlag(chatId);
        String newStatus;
        String text;
        if ("open".equals(current)) {
            newStatus = "close";
            text = "❗️AI进群昵称/简介检测已关闭";
        } else {
            newStatus = "open";
            text = "✅AI进群昵称/简介检测已打开";
        }
        if (groupInfoService.updateIntoGroupAiCheckFlag(chatId, newStatus)) {
            List<String> keywordsButtons = new ArrayList<>();
            KeywordsFormat keywordsFormat = new KeywordsFormat();
            keywordsButtons.add("🔑进群验证设置##changeGroupCheckStatus%%🎉是/否设置进群欢迎词##changeGroupWelcomeStatus");
            keywordsButtons.add("🔍是/否进群昵称违规检测##changeIntoGroupUserNameCheckStatus%%🤖是/否AI进群昵称/简介检测##changeIntoGroupAiCheckStatus");
            keywordsButtons.add("◀️返回主菜单##backMainMenu%%❌关闭菜单##closeMenu");
            keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + chatId + "</b>\n当前进群昵称验证状态：<b>" + groupInfoService.selAllByGroupId(chatId).getIntogroupusernamecheckflag() + "</b>\n当前AI进群昵称/简介检测状态：<b>" + newStatus + "</b>\n⚡️请选择一个操作!⚡️");
            keywordsFormat.setKeywordsButtons(keywordsButtons);
            sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
        }
    }

    public void changeGroupWelcomeStatus(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        String intoGroupWelcome = "";
        String text = "";
        GroupInfoWithBLOBs groupInfoWithBLOBs1 = new GroupInfoWithBLOBs();
        if ("close".equals(groupInfoWithBLOBs.getIntogroupwelcomeflag())) {
            groupInfoWithBLOBs1.setIntogroupwelcomeflag("open");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                intoGroupWelcome = "open";
                text = "✅进群欢迎功能已打开";
            }
        } else {
            groupInfoWithBLOBs1.setIntogroupwelcomeflag("close");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                intoGroupWelcome = "close";
                text = "❗️进群欢迎功能已关闭";
            }
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("🔑进群验证设置##changeGroupCheckStatus%%🎉设置进群欢迎词##changeGroupWelcomeStatus");
        keywordsButtons.add("🔍进群昵称违规检测##changeIntoGroupUserNameCheckStatus%%🤖是/否AI进群昵称/简介检测##changeIntoGroupAiCheckStatus");
        keywordsButtons.add("◀️返回主菜单##backMainMenu%%❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前群组验证状态：<b>" + groupInfoWithBLOBs.getIntogroupcheckflag() + "</b>\n当前进群欢迎状态：<b>" + intoGroupWelcome + "</b>\n当前进群昵称验证状态：<b>" + groupInfoWithBLOBs.getIntogroupusernamecheckflag() + "</b>\n当前AI进群昵称/简介检测状态：<b>" + groupInfoService.selIntoGroupAiCheckFlag(addRuleCacheMap.getGroupIdForUser(userId)) + "</b>\n⚡️请选择一个操作!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

    public void changeGroupCheckStatus(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        String mode = "计算型";
        if ("joinChannel".equals(groupInfoWithBLOBs.getCaptchamode())) {
            mode = "关注频道型";
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("📱切换计算型验证##changeToCompute%%💡切换至加入频道验证##changeToJoinChannel");
        keywordsButtons.add("🎈进群验证总开关##changeGroupCheckFlag%%◀️返回上一页##groupSetting");
        keywordsButtons.add("❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText("当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前群组验证状态：<b>" + groupInfoWithBLOBs.getIntogroupcheckflag() + "</b>\n当前进群验证模式：<b>" + mode + "</b>\n⚡️注意，在切换至关联频道验证之前，必须将Bot拉入群组关联频道，并赋予管理员权限!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

    public void changeGroupCheckFlag(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        String mode = "计算型";
        if ("joinChannel".equals(groupInfoWithBLOBs.getCaptchamode())) {
            mode = "关注频道型";
        }
        String intoGroupCheckFlag = "";
        String text = "";
        GroupInfoWithBLOBs groupInfoWithBLOBs1 = new GroupInfoWithBLOBs();
        if ("close".equals(groupInfoWithBLOBs.getIntogroupcheckflag())) {
            groupInfoWithBLOBs1.setIntogroupcheckflag("open");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                intoGroupCheckFlag = "open";
                text = "✅进群验证功能已打开";
            }
        } else {
            groupInfoWithBLOBs1.setIntogroupcheckflag("close");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                intoGroupCheckFlag = "close";
                text = "❗️进群验证功能已关闭";
            }
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("📱切换计算型验证##changeToCompute%%💡切换至加入频道验证##changeToJoinChannel");
        keywordsButtons.add("🎈进群验证总开关##changeGroupCheckFlag%%◀️返回上一页##groupSetting");
        keywordsButtons.add("❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前群组验证状态：<b>" + intoGroupCheckFlag + "</b>\n当前进群验证模式：<b>" + mode + "</b>\n⚡️注意，在切换至关联频道验证之前，必须将Bot拉入群组关联频道，并赋予管理员权限!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

    public void changeToCompute(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        GroupInfoWithBLOBs groupInfoWithBLOBs1 = new GroupInfoWithBLOBs();
        String text = "";
        String mode = "未知状态";
        String computeFlag = groupInfoWithBLOBs.getCaptchamode();
        if ("joinChannel".equals(computeFlag)) {
            groupInfoWithBLOBs1.setCaptchamode("compute");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                text += "✅已切换至计算型验证\n";
                mode = "计算型";
            }
        } else {
            text += "✅当前模式就是计算型验证\n";
            mode = "计算型";
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("📱切换计算型验证##changeToCompute%%💡切换至加入频道验证##changeToJoinChannel");
        keywordsButtons.add("🎈进群验证总开关##changeGroupCheckFlag%%◀️返回上一页##groupSetting");
        keywordsButtons.add("❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前群组验证状态：<b>" + groupInfoWithBLOBs.getIntogroupcheckflag() + "</b>\n当前进群验证模式：<b>" + mode + "</b>\n⚡️注意，在切换至关联频道验证之前，必须将Bot拉入群组关联频道，并赋予管理员权限!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

    public void changeToJoinChannel(AbsSender sender, Update update) throws TelegramApiException {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(addRuleCacheMap.getGroupIdForUser(userId));
        GroupInfoWithBLOBs groupInfoWithBLOBs1 = new GroupInfoWithBLOBs();
        String text = "";
        String mode = "未知状态";
        String computeFlag = groupInfoWithBLOBs.getCaptchamode();
        if ("compute".equals(computeFlag)) {
            groupInfoWithBLOBs1.setCaptchamode("joinChannel");
            if (groupInfoService.updateSelectiveByChatId(groupInfoWithBLOBs1, addRuleCacheMap.getGroupIdForUser(userId))) {
                text += "✅已切换至关注频道型验证\n";
                mode = "关注频道型";
            }
        } else {
            text += "✅当前模式就是关注频道型验证\n";
            mode = "关注频道型";
        }
        List<String> keywordsButtons = new ArrayList<>();
        KeywordsFormat keywordsFormat = new KeywordsFormat();
        keywordsButtons.add("📱切换计算型验证##changeToCompute%%💡切换至加入频道验证##changeToJoinChannel");
        keywordsButtons.add("🎈进群验证总开关##changeGroupCheckFlag%%◀️返回上一页##groupSetting");
        keywordsButtons.add("❌关闭菜单##closeMenu");
        keywordsFormat.setReplyText(text + "\n当前群组：<b>" + addRuleCacheMap.getGroupNameForUser(userId) + "</b>\n当前群组ID：<b>" + addRuleCacheMap.getGroupIdForUser(userId) + "</b>\n当前群组验证状态：<b>" + groupInfoWithBLOBs.getIntogroupcheckflag() + "</b>\n当前进群验证模式：<b>" + mode + "</b>\n⚡️注意，在切换至关联频道验证之前，必须将Bot拉入群组关联频道，并赋予管理员权限!⚡️");
        keywordsFormat.setKeywordsButtons(keywordsButtons);
        sender.execute(sendContent.editResponseMessage(update, keywordsFormat, "html"));
    }

}
