package top.feiyangdigital.utils.telegramRaw;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.feiyangdigital.entity.BaseInfo;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 直接调用 Telegram Bot API 的轻量封装。
 * <p>
 * 为什么需要它：本项目依赖的 telegrambots 6.9.7.1 的 {@code Chat} 类不包含
 * {@code personal_chat} 字段（Bot API 7.2 新增），库会直接忽略该字段。
 * 但 Bot API 是服务端能力，自己发一次 HTTP 请求即可读到原始 JSON。
 * <p>
 * 只用于读取，不承载任何写操作；所有异常一律返回 null，由调用方降级处理，
 * 绝不因为读不到资料而影响群管主流程。
 */
@Slf4j
@Component
public class TelegramRawApi {

    /** 进群流程有时间窗口，超时必须短，避免拖慢验证。 */
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build();

    /**
     * Bot API 根地址。
     * <p>
     * 固定使用官方端点：项目里的 telegrambots 客户端并未设置自定义 baseUrl
     * （所有 {@code GetChat} 等请求都直连 {@code api.telegram.org}），
     * 这里的原始请求必须与库保持同一条链路，否则会出现「库里能拿到 bio、这里拿不到」的不一致。
     * 若将来库改为走自建 API Server，这里需要同步调整。
     */
    private static final String API_BASE = "https://api.telegram.org";

    /**
     * 调用 {@code getChat} 并返回响应中的 {@code result} 对象。
     * <p>
     * 说明：该接口对用户 ID 调用时能否成功，与现有 {@code fetchUserBio} 的前提一致
     * （对方与 bot 有过交互、未被隐私设置限制等），失败时返回 null。
     *
     * @param chatId 用户 ID 或频道 ID（数字字符串）；也支持 {@code @username}
     * @return {@code result} 对象；参数缺失、网络异常、Telegram 返回 ok=false 时均为 null
     */
    public JSONObject getChatRaw(String chatId) {
        String token = BaseInfo.getBotToken();
        if (!StringUtils.hasText(token) || !StringUtils.hasText(chatId)) {
            return null;
        }
        String url = API_BASE + "/bot" + token + "/getChat?chat_id=" + chatId;
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.body() == null) {
                return null;
            }
            String body = response.body().string();
            JSONObject json = JSONObject.parseObject(body);
            if (json == null || !Boolean.TRUE.equals(json.getBoolean("ok"))) {
                log.warn("getChat 原始调用失败 chatId={}, body={}", chatId, abbreviate(body));
                return null;
            }
            return json.getJSONObject("result");
        } catch (IOException | RuntimeException e) {
            log.warn("getChat 原始调用异常 chatId={}, err={}", chatId, e.getMessage());
            return null;
        }
    }

    /** 日志里避免打印超长响应体。 */
    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }
}
