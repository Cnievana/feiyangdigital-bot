package top.feiyangdigital.utils;

import com.mchange.lang.IntegerUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.feiyangdigital.utils.groupCaptch.CaptchaManager;
import top.feiyangdigital.utils.groupCaptch.CaptchaManagerCacheMap;
import top.feiyangdigital.utils.ruleCacheMap.AddRuleCacheMap;
import top.feiyangdigital.utils.ruleCacheMap.DeleteRuleCacheMap;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

@Component
public class TimerDelete {

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private CheckUser checkUser;

    @Autowired
    private AddRuleCacheMap addRuleCacheMap;

    @Autowired
    private DeleteRuleCacheMap deleteRuleCacheMap;

    @Autowired
    private SendContent sendContent;

    @Autowired
    private CaptchaManager captchaManager;

    @Autowired
    private CaptchaManagerCacheMap captchaManagerCacheMap;

    public void deletePrivateMessageImmediately(AbsSender sender, Update update) {
        String userId = update.getCallbackQuery().getFrom().getId().toString();
        addRuleCacheMap.updateUserMapping(userId, addRuleCacheMap.getGroupIdForUser(userId), addRuleCacheMap.getGroupNameForUser(userId), "notallow", addRuleCacheMap.getAiFlagForUser(userId), addRuleCacheMap.getCrontabFlagForUser(userId));
        deleteRuleCacheMap.updateUserMapping(userId, deleteRuleCacheMap.getGroupIdForUser(userId), deleteRuleCacheMap.getGroupNameForUser(userId), "notdelete");
        try {
            sender.execute(new DeleteMessage(update.getCallbackQuery().getMessage().getChatId().toString(), ((Message) update.getCallbackQuery().getMessage()).getMessageId()));
            sender.execute(sendContent.messageText(update, "当前群组ID：" + addRuleCacheMap.getGroupIdForUser(userId) + " \n当前可输入状态：" + addRuleCacheMap.getKeywordsFlagForUser(userId) + "\n你已退出 " + addRuleCacheMap.getGroupNameForUser(userId) + " 的设置"));
        } catch (TelegramApiException e) {
            // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
        }
    }

    public void deletePrivateUsualMessageImmediately(AbsSender sender, Update update) {
        try {
            sender.execute(new DeleteMessage(update.getCallbackQuery().getMessage().getChatId().toString(), ((Message) update.getCallbackQuery().getMessage()).getMessageId()));
            sender.execute(sendContent.messageText(update, "你已退出设置"));
        } catch (TelegramApiException e) {
            // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
        }
    }

    public void deleteMessageImmediately(AbsSender sender, Update update) throws TelegramApiException {
        if (!checkUser.isGroupAdmin(sender, update)) {
            try {
                sender.execute(new DeleteMessage(update.getMessage().getChatId().toString(), update.getMessage().getMessageId()));
            } catch (TelegramApiException e) {
                // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
            }
        }
    }

    public void deleteMessageAfterDelay(AbsSender sender, Update update, int delayInSeconds) throws TelegramApiException {
        if (!checkUser.isGroupAdmin(sender, update)) {
            Runnable task = () -> {
                try {
                    sender.execute(new DeleteMessage(update.getMessage().getChatId().toString(), update.getMessage().getMessageId()));
                } catch (TelegramApiException e) {
                    // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
                }
            };
            taskScheduler.schedule(task, Instant.now().plusSeconds(delayInSeconds));
        }
    }

    public <T extends Serializable, Method extends BotApiMethod<T>> String sendTimedMessage(AbsSender sender, Object sendRealType, int delayInSeconds) {
        Message response = null;
        try {
            if (sendRealType instanceof SendMessage) {
                response = sender.execute((SendMessage) sendRealType);
            } else if (sendRealType instanceof SendVideo) {
                response = sender.execute((SendVideo) sendRealType);
            } else if (sendRealType instanceof SendPhoto) {
                response = sender.execute((SendPhoto) sendRealType);
            } else {
                throw new RuntimeException("类型错误:" + sendRealType.getClass().getName());
            }
        } catch (TelegramApiException e) {
            // 这里可以捕获异常，但是我们可以选择不执行任何操作
        }
        if (response != null) {
            Integer messageId = response.getMessageId();
            Message finalResponse = response;
            Runnable task = () -> {
                try {
                    sender.execute(new DeleteMessage(finalResponse.getChatId().toString(), messageId));
                } catch (TelegramApiException e) {
                    // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
                }
            };
            taskScheduler.schedule(task, Instant.now().plusSeconds(delayInSeconds));
            return messageId.toString();
        }
        return null;
    }

    public <T extends Serializable, Method extends BotApiMethod<T>> String sendAndDeleteMessageImmediately(AbsSender sender, Method method) throws TelegramApiException {
        Serializable response = sender.execute(method);
        if (response instanceof Message) {
            Message sentMessage = (Message) response;
            sender.execute(new DeleteMessage(sentMessage.getChatId().toString(), sentMessage.getMessageId()));
            return sentMessage.getMessageId().toString();
        }
        return null;
    }

    public void deleteMessageAndNotifyAfterDelay(AbsSender sender, SendMessage sendMessage, String chatId, Integer messageId, int delayInSeconds, Long userId, int notifyDelay) {
        Runnable task = () -> {
            try {
                if (messageId != null) {
                    sender.execute(new DeleteMessage(chatId, messageId));
                }
                captchaManager.clearMappingsForUser(userId.toString());
                captchaManagerCacheMap.clearMappingsForUser(userId.toString(), chatId);
                // 在此发送提示消息
                Message message = sender.execute(sendMessage);
                deleteMessageByMessageIdDelay(sender, chatId, message.getMessageId(), notifyDelay);
            } catch (TelegramApiException e) {
                // 如果您仍希望不论是否出现异常都发送提示，那么将发送提示的逻辑移至catch块外部
            }
        };
        taskScheduler.schedule(task, Instant.now().plusSeconds(delayInSeconds));
    }

    /**
     * 延迟 delayInSeconds 秒后，将「原验证消息」原地编辑为超时提示（同时清除验证按钮），
     * 再于 deleteAfterSeconds 秒后删除这条被编辑过的消息（保持原「超时提示会自动消失」的观感）。
     * <p>
     * 与原 deleteMessageAndNotifyAfterDelay 的关键区别：
     * 1）不再「先删原消息、再另发一条新通知」，而是直接在原消息上编辑，避免刷屏打扰群成员；
     * 2）编辑发生在删除之前，消息内容先被替换，随后才被删除；
     * 3）若原消息已不存在（例如用户已在超时前通过验证、原消息已被删除），
     *    EditMessageText 会抛异常并被吞掉，后续清理与删除都不会执行，
     *    因此不会对已验证用户误发「未验证 / 永久限制发言」提示。
     */
    public void editMessageAndNotifyAfterDelay(AbsSender sender, String chatId, Integer messageId, String text, Long userId, int delayInSeconds, int deleteAfterSeconds) {
        Runnable task = () -> {
            try {
                if (messageId == null) {
                    return;
                }
                // 显式传入空的 inline keyboard，用于清除原消息上的「管理员解禁 / 点击验证」按钮
                InlineKeyboardMarkup emptyMarkup = new InlineKeyboardMarkup();
                emptyMarkup.setKeyboard(new ArrayList<>());
                EditMessageText editMessageText = new EditMessageText();
                editMessageText.setChatId(chatId);
                editMessageText.setMessageId(messageId);
                editMessageText.setText(text);
                editMessageText.setParseMode(ParseMode.HTML);
                editMessageText.setReplyMarkup(emptyMarkup);
                sender.execute(editMessageText);
                // 编辑成功后再清理验证缓存，并安排稍后删除这条提示消息
                captchaManager.clearMappingsForUser(userId.toString());
                captchaManagerCacheMap.clearMappingsForUser(userId.toString(), chatId);
                deleteMessageByMessageIdDelay(sender, chatId, messageId, deleteAfterSeconds);
            } catch (TelegramApiException e) {
                // 原消息可能已被删除（例如用户已通过验证），此时不做任何处理，避免误报超时
            }
        };
        taskScheduler.schedule(task, Instant.now().plusSeconds(delayInSeconds));
    }

    public Integer deleteMessageImmediatelyAndNotifyAfterDelay(AbsSender sender, Object sendRealType, String chatId, Integer messageId, Long userId, int notifyDelay) {
        Integer msgId = 0;
        try {
            if (messageId != null) {
                sender.execute(new DeleteMessage(chatId, messageId));
            }
            captchaManager.clearMappingsForUser(userId.toString());
            captchaManagerCacheMap.clearMappingsForUser(userId.toString(), chatId);
            // 在此发送提示消息
            Message message;
            if (sendRealType instanceof SendMessage) {
                message = sender.execute((SendMessage) sendRealType);
            } else if (sendRealType instanceof SendVideo) {
                message = sender.execute((SendVideo) sendRealType);
            } else if (sendRealType instanceof SendPhoto) {
                message = sender.execute((SendPhoto) sendRealType);
            } else {
                throw new RuntimeException("类型错误:" + sendRealType.getClass().getName());
            }
            msgId = message.getMessageId();
            deleteMessageByMessageIdDelay(sender, chatId, msgId, notifyDelay);
        } catch (TelegramApiException e) {
            // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
        }
        return msgId;
    }

    public void deleteMessageByMessageIdDelay(AbsSender sender, String chatId, Integer messageId, int delayInSeconds) {
        Runnable task = () -> {
            try {
                if (messageId != null) {
                    sender.execute(new DeleteMessage(chatId, messageId));
                }
            } catch (TelegramApiException e) {
                // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
            }
        };
        taskScheduler.schedule(task, Instant.now().plusSeconds(delayInSeconds));
    }

    public boolean deleteByMessageIdImmediately(AbsSender sender, String chatId, Integer messageId) {
        try {
            if (messageId != null) {
                sender.execute(new DeleteMessage(chatId, messageId));
            }
            return true;
        } catch (TelegramApiException e) {
            return false;
            // 这里可以捕获异常，但是我们可以选择不执行任何操作，因为我们不关心消息是否确实已经被删除
        }
    }
}

