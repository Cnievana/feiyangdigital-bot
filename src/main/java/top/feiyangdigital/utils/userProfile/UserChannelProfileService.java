package top.feiyangdigital.utils.userProfile;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.feiyangdigital.utils.telegramRaw.TelegramRawApi;

/**
 * 读取用户「个人频道（personal channel）」画像，供进群检测使用。
 * <p>
 * 背景：广告号会把广告写在「个人频道」的名称 / 简介 / 置顶消息里，而昵称、用户名、简介
 * 本身保持干净，从而绕开现有的昵称/简介检测。Bot API 7.2 起
 * {@code getChat(user_id)} 会返回 {@code personal_chat}，其中直接带有频道标题，
 * 这就是本类要采集的核心证据。
 * <p>
 * 输出格式（直接拼进 AI 检测内容与正则匹配串）：
 * <pre>
 * 个人频道：代充值项目 每天保底500+
 * 频道简介：长期稳定无风险，速度来人
 * 频道置顶：...
 * </pre>
 * 用户没有个人频道、频道被隐藏、或接口失败时返回空字符串，调用方按「无频道」处理。
 */
@Slf4j
@Service
public class UserChannelProfileService {

    /** 单个字段截断长度，避免超长内容污染正则与提示词。 */
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_PINNED_LENGTH = 300;

    @Autowired
    private TelegramRawApi telegramRawApi;

    /**
     * 获取该用户的个人频道画像。
     * <p>
     * 结果按 userId 缓存（TTL 见 ehcache.xml）：广告号常反复退群/进群，
     * 缓存可显著减少 Telegram API 调用；拿不到时缓存空串，避免反复重试。
     */
    @Cacheable(value = "userChannelProfile", key = "#userId")
    public String getChannelProfile(String userId) {
        JSONObject user = telegramRawApi.getChatRaw(userId);
        if (user == null) {
            return "";
        }
        JSONObject personalChat = user.getJSONObject("personal_chat");
        if (personalChat == null) {
            return "";
        }
        // personal_chat 本身就是 Chat 对象：即使频道私有、bot 无法 getChat(channel)，
        // 这里的 title 依然可用，足以作为判定依据。
        String title = truncate(personalChat.getString("title"), MAX_TITLE_LENGTH);
        String username = truncate(personalChat.getString("username"), MAX_TITLE_LENGTH);
        long channelId = personalChat.getLongValue("id");

        String description = "";
        String pinned = "";
        if (channelId != 0L) {
            JSONObject channel = telegramRawApi.getChatRaw(String.valueOf(channelId));
            if (channel != null) {
                description = truncate(channel.getString("description"), MAX_DESCRIPTION_LENGTH);
                pinned = truncate(pinnedText(channel), MAX_PINNED_LENGTH);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("个人频道：").append(StringUtils.hasText(title) ? title : "（无名称）");
        if (StringUtils.hasText(username)) {
            sb.append("（@").append(username).append("）");
        }
        sb.append("\n");
        if (StringUtils.hasText(description)) {
            sb.append("频道简介：").append(description).append("\n");
        }
        if (StringUtils.hasText(pinned)) {
            sb.append("频道置顶：").append(pinned).append("\n");
        }
        String profile = sb.toString().trim();
        log.warn("检测到用户个人频道 userId={}, 画像={}", userId, profile.replace("\n", " / "));
        return profile;
    }

    /** 频道置顶消息的文本（可能是纯文字，也可能是媒体配文）。 */
    private static String pinnedText(JSONObject channel) {
        JSONObject pinned = channel.getJSONObject("pinned_message");
        if (pinned == null) {
            return "";
        }
        String text = pinned.getString("text");
        if (!StringUtils.hasText(text)) {
            text = pinned.getString("caption");
        }
        return text == null ? "" : text;
    }

    private static String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
