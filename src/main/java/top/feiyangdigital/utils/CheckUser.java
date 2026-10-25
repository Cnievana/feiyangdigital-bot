package top.feiyangdigital.utils;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberAdministrator;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberOwner;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.handleService.SpamChannelBotService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class CheckUser {

    @Autowired
    private AdminList adminList;

    @CacheEvict(value = "adminsCache", allEntries = true)
    public String fetchHighAdminList(AbsSender sender, Update update) throws TelegramApiException {
        return adminList.getAdmins(sender, update.getMessage().getChatId().toString()).stream()
                .filter(admin ->
                        (admin instanceof ChatMemberAdministrator && ((ChatMemberAdministrator) admin).getIsAnonymous()) || admin instanceof ChatMemberOwner
                ).map(admin -> String.valueOf(admin.getUser().getId()))
                .collect(Collectors.joining("|"));
    }

    public boolean isGroupChannel(AbsSender sender, Update update) throws TelegramApiException {
        Chat senderChat = update.getMessage().getSenderChat();
        String chatId = update.getMessage().getChatId().toString();
        if (senderChat != null && "channel".equals(senderChat.getType())) {
            GetChat getChat = GetChat.builder().chatId(chatId).build();
            Chat checkChat = sender.execute(getChat);
            return checkChat.getLinkedChatId() != null && senderChat.getId().equals(checkChat.getLinkedChatId());
        }
        return false;
    }


    public boolean isGroupAdmin(AbsSender sender, Update update) throws TelegramApiException {
        for (ChatMember admin : adminList.getAdmins(sender, update.getMessage().getChatId().toString())) {
            if ("GroupAnonymousBot".equals(update.getMessage().getFrom().getUserName()) || admin.getUser().getId().equals(update.getMessage().getFrom().getId()) || isGroupChannel(sender, update)) {
                return true;
            }
        }
        return false; // 如果用户不是管理员
    }

    /**
     * 按「群ID + 用户ID」判断是否管理员。
     * <p>
     * 供没有 {@link Update} 的场景复用（例如 AI 自动禁言时的管理员豁免判断），
     * 与 {@link #isGroupAdmin(AbsSender, Update)} 共用同一份 {@code adminsCache}。
     */
    public boolean isGroupAdmin(AbsSender sender, String chatId, Long userId) throws TelegramApiException {
        for (ChatMember admin : adminList.getAdmins(sender, chatId)) {
            if (admin.getUser().getId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isChatOwner(AbsSender sender, Update update) throws TelegramApiException {
        String chatId;
        long uid;
        if (update.getMessage() == null) {
            chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            uid = update.getCallbackQuery().getFrom().getId();
        } else {
            chatId = update.getMessage().getChatId().toString();
            uid = update.getMessage().getFrom().getId();
        }
        for (ChatMember admin : adminList.getAdmins(sender, chatId)) {
            if (admin.getUser().getId().equals(uid)) {
                if (admin instanceof ChatMemberOwner) {
                    return true;
                }

            }
        }
        return false;
    }

    public Map<String, String> getChatOwner(AbsSender sender, Update update) throws TelegramApiException {
        Map<String, String> map = new ConcurrentHashMap<>();
        String chatId = "";
        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId().toString();
        }
        for (ChatMember admin : adminList.getAdmins(sender, chatId)) {
            if (admin instanceof ChatMemberOwner) {
                map.put("ownerId", admin.getUser().getId().toString());
                String userName = StrUtil.concat(true, admin.getUser().getFirstName(), admin.getUser().getLastName());
                map.put("ownerName", userName);
            }
        }
        return map;
    }

    @Cacheable(value = "linkedChatInfo", key = "#chatId")
    public Map<String,String> getLinkedChatInfo(AbsSender sender, String chatId) throws TelegramApiException {
        Map<String,String> map = new ConcurrentHashMap<>();
        GetChat getChat = GetChat.builder().chatId(chatId).build();
        Chat checkChat = sender.execute(getChat);
        checkChat.getUserName();
        map.put("LinkedChatId",checkChat.getLinkedChatId().toString());
        GetChat getChat1 = GetChat.builder().chatId(checkChat.getLinkedChatId()).build();
        Chat checkChat1 = sender.execute(getChat1);
        map.put("LinkedChatString","https://t.me/"+checkChat1.getUserName());
        return map;
    }

}
