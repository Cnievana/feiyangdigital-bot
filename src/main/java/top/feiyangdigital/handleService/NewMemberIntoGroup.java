package top.feiyangdigital.handleService;

import com.unfbx.chatgpt.entity.chat.ChatChoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.entity.BaseInfo;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.sqlService.BotRecordService;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.SendContent;
import top.feiyangdigital.utils.TimerDelete;
import top.feiyangdigital.utils.aiMessageCheck.AiResultParser;
import top.feiyangdigital.utils.groupCaptch.CaptchaManagerCacheMap;
import top.feiyangdigital.utils.groupCaptch.GroupMessageIdCacheMap;
import top.feiyangdigital.utils.groupCaptch.RestrictOrUnrestrictUser;
import top.feiyangdigital.utils.userProfile.UserChannelProfileService;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NewMemberIntoGroup {

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private BotRecordService botRecordService;

    @Autowired
    private RestrictOrUnrestrictUser restrictOrUnrestrictUser;

    @Autowired
    private SendContent sendContent;

    @Autowired
    private TimerDelete timerDelete;

    @Autowired
    private CaptchaManagerCacheMap captchaManagerCacheMap;

    @Autowired
    private GroupMessageIdCacheMap groupMessageIdCacheMap;

    @Autowired
    private OpenAiApiService openAiApiService;

    @Autowired
    private UserChannelProfileService userChannelProfileService;

    /** AI 判定为广告的阈值（与群内消息检测保持一致）。 */
    private static final int AI_SPAM_THRESHOLD = 6;


    public void handleMessage(AbsSender sender, Update update, User outUser) throws TelegramApiException {


        Long userId;
        String firstName;
        String lastName;
        Long chatId;
        String groupTitle;
        String joinedTime;
        if (outUser == null) {
            ChatMember member = update.getChatMember().getNewChatMember();
            userId = member.getUser().getId();
            firstName = member.getUser().getFirstName();
            lastName = member.getUser().getLastName();
            chatId = update.getChatMember().getChat().getId();
            groupTitle = update.getChatMember().getChat().getTitle();
            joinedTime = update.getChatMember().getDate().toString();
        } else {
            userId = outUser.getId();
            firstName = outUser.getFirstName();
            lastName = outUser.getLastName();
            chatId = update.getMessage().getChat().getId();
            groupTitle = update.getMessage().getChat().getTitle();
            joinedTime = update.getMessage().getDate().toString();
        }

        if (lastName == null) {
            lastName = "";
        }

        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(chatId.toString());

        // 读取一次个人简介，供「正则昵称/简介检测」与「AI进群检测」共用。
        // 拿不到（对方隐私设置等）时为 null，不影响昵称匹配，也不会因此误封。
        String bio = fetchUserBio(sender, userId);

        // 读取个人频道画像（频道名/简介/置顶）：广告号常把广告写在这里，而昵称与简介保持干净。
        // 仅在「AI进群检测」或「进群昵称/简介检测」开启时才请求，避免无谓的 API 调用；
        // 未设置个人频道或被隐藏时为空串，不影响其他检测。
        boolean checkChannelProfile = "open".equals(groupInfoService.selIntoGroupAiCheckFlag(chatId.toString()))
                || (groupInfoWithBLOBs != null && "open".equals(groupInfoWithBLOBs.getIntogroupusernamecheckflag()));
        String channelProfile = checkChannelProfile
                ? userChannelProfileService.getChannelProfile(userId.toString())
                : "";

        // 进群链路诊断日志：排查「广告号进群没被拦住」时，先看这两条是否输出。
        log.warn("进群检测开始 groupId={}, userId={}, name={}, 进群昵称检测={}, AI进群检测={}",
                chatId, userId, firstName,
                groupInfoWithBLOBs == null ? "群未初始化" : groupInfoWithBLOBs.getIntogroupusernamecheckflag(),
                checkChannelProfile ? "open" : "close");
        log.warn("进群检测资料 groupId={}, userId={}, bio={}, 个人频道画像={}",
                chatId, userId, bio == null ? "（取不到）" : bio,
                StringUtils.hasText(channelProfile) ? channelProfile.replace("\n", " / ") : "（无个人频道）");

        if (intoGroupNameCheck(sender, userId, chatId, groupInfoWithBLOBs, firstName, lastName, bio, channelProfile)) {
            return;
        }

        // AI 进群昵称/简介检测：命中则永久限制并直接返回，不再进入验证流程
        if (aiCheckNicknameAndBio(sender, userId, chatId, firstName, lastName, groupInfoWithBLOBs, bio, channelProfile)) {
            return;
        }

        if (groupInfoWithBLOBs != null && "close".equals(groupInfoWithBLOBs.getIntogroupcheckflag()) && "close".equals(groupInfoWithBLOBs.getIntogroupwelcomeflag())) {
            botRecordService.addUserRecord(chatId.toString(), userId.toString(), joinedTime);
        }

        if (groupInfoWithBLOBs != null && "close".equals(groupInfoWithBLOBs.getIntogroupcheckflag()) && "open".equals(groupInfoWithBLOBs.getIntogroupwelcomeflag())) {
            if (StringUtils.hasText(groupInfoWithBLOBs.getKeywords()) && groupInfoWithBLOBs.getKeywords().contains("&&welcome=")) {
                List<KeywordsFormat> keywordsFormatList = Arrays.stream(groupInfoWithBLOBs.getKeywords().split("\\n{2,}"))
                        .map(String::trim)
                        .map(KeywordsFormat::new)
                        .collect(Collectors.toList());
                for (KeywordsFormat keywordFormat : keywordsFormatList) {
                    Map<String, String> currentMap = keywordFormat.getRuleMap();
                    if (currentMap.containsKey("DelWelcome")) {
                        KeywordsFormat newKeyFormat = new KeywordsFormat();
                        newKeyFormat.setKeywordsButtons(keywordFormat.getKeywordsButtons());
                        String text = keywordFormat.getReplyText()
                                .replaceAll("@userId", String.format("<b><a href=\"tg://user?id=%d\">%s</a></b>", userId, firstName))
                                .replaceAll("@groupName", String.format("<b>%s</b>", groupInfoWithBLOBs.getGroupname()));
                        newKeyFormat.setReplyText(text);
                        if (keywordFormat.getVideoUrl() != null) {
                            newKeyFormat.setVideoUrl(keywordFormat.getVideoUrl());
                        } else if (keywordFormat.getPhotoUrl() != null) {
                            newKeyFormat.setPhotoUrl(keywordFormat.getPhotoUrl());
                        }
                        Object response = sendContent.createGroupMessage(chatId.toString(), newKeyFormat, "html");
                        String msgId;
                        if (keywordFormat.getPhotoUrl() != null) {
                            SendPhoto sendPhoto = (SendPhoto) response;
                            msgId = timerDelete.sendTimedMessage(sender, sendPhoto, Integer.parseInt(currentMap.get("DelWelcome")));
                        } else if (keywordFormat.getVideoUrl() != null) {
                            SendVideo sendVideo = (SendVideo) response;
                            msgId = timerDelete.sendTimedMessage(sender, sendVideo, Integer.parseInt(currentMap.get("DelWelcome")));
                        } else {
                            SendMessage sendMessage = (SendMessage) response;
                            sendMessage.setDisableWebPagePreview(true);
                            msgId = timerDelete.sendTimedMessage(sender, sendMessage, Integer.parseInt(currentMap.get("DelWelcome")));
                        }
                        if (groupMessageIdCacheMap.getMapSize() > 0) {
                            groupMessageIdCacheMap.deleteAllMessage(sender, chatId.toString());
                        }
                        groupMessageIdCacheMap.setGroupMessageId(chatId.toString(), Integer.valueOf(msgId));
                    }
                }
            }
            botRecordService.addUserRecord(chatId.toString(), userId.toString(), joinedTime);
        }

        if (groupInfoWithBLOBs != null && "open".equals(groupInfoWithBLOBs.getIntogroupcheckflag())) {
            String url = String.format("https://t.me/%s?start=_intoGroupInfo%sand%s", BaseInfo.getBotName(), chatId.toString(), userId.toString());
            restrictOrUnrestrictUser.restrictUser(sender, userId, chatId.toString(), 0L);
            KeywordsFormat keywordsFormat = new KeywordsFormat();
            List<String> keywordsButtons = new ArrayList<>();
            keywordsButtons.add("👥管理员解禁##adminUnrestrict" + userId);
            keywordsButtons.add("❗️点击验证$$" + url);
            keywordsFormat.setKeywordsButtons(keywordsButtons);
            String text = String.format("欢迎 <b><a href=\"tg://user?id=%d\">%s</a></b> 加入<b> %s </b>, 现在你需要在<b>90秒内</b>点击下面的验证按钮完成验证，超时将永久限制发言！", userId, firstName, groupTitle);
            keywordsFormat.setReplyText(text);
            Message message1 = sender.execute((SendMessage) sendContent.createResponseMessage(update, keywordsFormat, "html"));
            Integer messageId = message1.getMessageId();
            captchaManagerCacheMap.updateUserMapping(userId.toString(), chatId.toString(), 0, messageId);
            String text1 = String.format("用户 <b><a href=\"tg://user?id=%d\">%s</a></b> 在 <b>90秒内</b> 未进行验证，永久限制发言！", userId, firstName);
            // 超时后：直接在原验证消息上编辑为提示（并清除验证按钮），20 秒后再删除，
            // 避免额外再发一条消息打扰群成员（原实现为「先删原消息 + 另发新通知」）
            timerDelete.editMessageAndNotifyAfterDelay(sender, chatId.toString(), messageId, text1, userId, 90, 20);
        }
    }

    public boolean intoGroupNameCheck(AbsSender sender, Long userId, Long chatId, GroupInfoWithBLOBs groupInfoWithBLOBs, String firstName, String lastName, String bio, String channelProfile) {
        if (groupInfoWithBLOBs != null && "open".equals(groupInfoWithBLOBs.getIntogroupusernamecheckflag())) {
            if (StringUtils.hasText(groupInfoWithBLOBs.getKeywords()) && groupInfoWithBLOBs.getKeywords().contains("&&intoGroupBan=")) {
                List<KeywordsFormat> keywordsFormatList = Arrays.stream(groupInfoWithBLOBs.getKeywords().split("\\n{2,}"))
                        .map(String::trim)
                        .map(KeywordsFormat::new)
                        .collect(Collectors.toList());
                for (KeywordsFormat keywordFormat : keywordsFormatList) {
                    Map<String, String> currentMap = keywordFormat.getRuleMap();
                    if (currentMap.containsKey("DelIntoGroupBan")) {
                        String regex = keywordFormat.getRegex();
                        Pattern pattern = Pattern.compile(regex);
                        // 昵称、「个人简介」或「个人频道画像」任一命中即判定违规
                        boolean matched = pattern.matcher(firstName).find()
                                || pattern.matcher(lastName).find()
                                || pattern.matcher(firstName + lastName).find()
                                || (StringUtils.hasText(bio) && pattern.matcher(bio).find())
                                || (StringUtils.hasText(channelProfile) && pattern.matcher(channelProfile).find());
                        if (matched) {
                            restrictOrUnrestrictUser.restrictUser(sender, userId, chatId.toString(), 0L);
                            KeywordsFormat newKeyFormat = new KeywordsFormat();
                            newKeyFormat.setKeywordsButtons(keywordFormat.getKeywordsButtons());
                            String text = keywordFormat.getReplyText()
                                    .replaceAll("@userId", String.format("<b><a href=\"tg://user?id=%d\">%s</a></b>", userId, firstName))
                                    .replaceAll("@groupName", String.format("<b>%s</b>", groupInfoWithBLOBs.getGroupname()));
                            newKeyFormat.setReplyText(text);
                            // 仅在配置了按钮时设置，避免因按钮缺失导致提示发送失败
                            if (keywordFormat.getKeywordsButtons() != null && !keywordFormat.getKeywordsButtons().isEmpty()) {
                                newKeyFormat.setKeywordsButtons(Collections.singletonList(keywordFormat.getKeywordsButtons().get(0).replaceAll("@singleUserId", userId.toString())));
                            }
                            SendMessage sendMessage = (SendMessage) sendContent.createGroupMessage(chatId.toString(), newKeyFormat, "html");
                            sendMessage.setDisableWebPagePreview(true);
                            String msgId = timerDelete.sendTimedMessage(sender, sendMessage, Integer.parseInt(currentMap.get("DelIntoGroupBan")));
                            if (StringUtils.hasText(msgId)) {
                                captchaManagerCacheMap.updateUserMapping(userId.toString(), chatId.toString(), 0, Integer.valueOf(msgId));
                            }
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * AI 进群昵称/简介/个人频道检测：把新成员的昵称、个人简介，以及其绑定的个人频道
     * （频道名 / 简介 / 置顶）一并交给 DeepSeek 判定，
     * 判定为广告（spamChance >= 阈值）则直接永久限制发言并返回 true（调用方据此跳过验证流程）。
     * <p>
     * 安全设计：仅在群开启「AI进群昵称/简介检测」时执行；
     * AI 调用失败、返回为空/非法 JSON、缺少 spamChance 等异常情况一律返回 false（放行），
     * 绝不会因为 AI 出错而误封正常用户。
     */
    private boolean aiCheckNicknameAndBio(AbsSender sender, Long userId, Long chatId, String firstName, String lastName,
                                          GroupInfoWithBLOBs groupInfoWithBLOBs, String bio, String channelProfile) {
        if (groupInfoWithBLOBs == null || !"open".equals(groupInfoService.selIntoGroupAiCheckFlag(chatId.toString()))) {
            return false;
        }
        String content = "用户昵称：" + firstName + " " + (lastName == null ? "" : lastName)
                + "\n个人简介：" + (bio == null ? "（无）" : bio)
                + (StringUtils.hasText(channelProfile) ? "\n" + channelProfile : "");
        List<ChatChoice> choices;
        try {
            choices = openAiApiService.getOpenAiAnalyzeResult(content);
        } catch (Exception e) {
            return false;
        }
        if (choices == null || choices.isEmpty() || choices.get(0).getMessage() == null
                || !StringUtils.hasText(choices.get(0).getMessage().getContent())) {
            return false;
        }
        String raw = choices.get(0).getMessage().getContent();
        AiResultParser.SpamResult result = AiResultParser.parseSpam(raw);
        if (result == null) {
            log.warn("AI进群检测返回内容无法解析出spamChance，放行。groupId={}, userId={}, 返回内容={}", chatId, userId, raw);
            return false;
        }
        Integer spamChance = result.getSpamChance();
        if (spamChance < AI_SPAM_THRESHOLD) {
            log.warn("AI进群检测未命中 groupId={}, userId={}, spamChance={}", chatId, userId, spamChance);
            return false;
        }
        String spamReason = result.getSpamReason();
        restrictOrUnrestrictUser.restrictUser(sender, userId, chatId.toString(), 0L);
        String text = String.format("用户 <b><a href=\"tg://user?id=%d\">%s</a></b> 因昵称/简介/个人频道被AI判定为广告，已被永久限制发言！\n判定原因：<tg-spoiler>%s</tg-spoiler>",
                userId, firstName, spamReason == null ? "" : spamReason);
        SendMessage notification = new SendMessage();
        notification.setChatId(chatId);
        notification.setText(text);
        notification.setParseMode(ParseMode.HTML);
        // 提示消息 60 秒后自动删除，避免刷屏
        timerDelete.sendTimedMessage(sender, notification, 60);
        return true;
    }

    /**
     * 读取用户的个人简介（Telegram Bot API getChat 在私聊中返回 bio）。
     * 任何异常（对方未与 bot 交互、隐私限制、权限不足等）或取到空值都返回 null，
     * 由调用方按「无简介」处理，避免因读不到简介而误封正常用户。
     */
    private String fetchUserBio(AbsSender sender, Long userId) {
        try {
            GetChat getChat = GetChat.builder().chatId(userId.toString()).build();
            Chat chat = sender.execute(getChat);
            return chat == null ? null : chat.getBio();
        } catch (TelegramApiException e) {
            return null;
        }
    }

}
