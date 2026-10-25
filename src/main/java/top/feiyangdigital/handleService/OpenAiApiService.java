package top.feiyangdigital.handleService;

import com.alibaba.fastjson2.JSONReader;
import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.chat.ChatChoice;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.entity.chat.ResponseFormat;
import com.unfbx.chatgpt.interceptor.DynamicKeyOpenAiAuthInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.feiyangdigital.entity.BaseInfo;
import top.feiyangdigital.entity.DeepSeekChatCompletion;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class OpenAiApiService {

    private static final String ANALYSIS_TEMPLATE = "你是一名聊天应用的群组管理员和垃圾信息检测器。你的任务是根据用户发言内容，判断其是否涉及垃圾广告、黄赌毒、引流、非法服务、虚拟货币交易、黑灰产、以及壮阳、性用品、迷药、夸张致富广告等内容。\n" +
            "请根据以下问题逐项进行详细判断：\n" +
            "1. **是否包含敏感关键词**：发言中是否提及以下关键词或相关变种词：区块链、虚拟货币（如比特币、以太坊等）、赚钱、黑产、灰产、赌博、色情、毒品、金融诈骗、个人隐私交易、洗钱、快速致富、暴富、提现、壮阳药、性用品、迷药等？\n" +
            "2. **非法服务或活动**：用户是否提供或引导他人参与非法服务或活动，例如：\n" +
            "   - 提供虚假支付信息或银行卡号；\n" +
            "   - 诱导用户加入社交群组（不限于QQ、微信，涵盖任何社交平台群，如Telegram、Discord等），点击虚假链接或参与欺诈活动；\n" +
            "   - 涉及赌博平台、色情服务、毒品交易、贩卖非法物品、虚拟货币交易所等；\n" +
            "   - 涉嫌黑灰产，例如虚假票据、代开发票、黑客攻击服务等。\n" +
            "3. **引流和推广**：\n" +
            "   - 用户是否试图诱导他人点击个人简介、私信联系、加好友、或加入任何社交群组（包括但不限于QQ、微信、Telegram、Discord等）；\n" +
            "   - 是否推广某些平台、项目、APP下载链接，或通过发放红包、优惠券吸引用户；\n" +
            "   - 是否推广高风险金融产品、投资项目或类似传销的内容。\n" +
            "4. **色情与性用品相关内容**：\n" +
            "   - 发言中是否包含任何形式的色情内容（如图片、视频、网站链接），或涉及性交易、成人服务、性用品（如壮阳药、情趣用品等）的推广；\n" +
            "   - 是否推广成人内容平台，或暗示性服务（如“约炮”、“裸聊”、“一夜情”等）。\n" +
            "5. **赌博相关内容**：\n" +
            "   - 发言中是否提及赌博相关内容，包括在线赌博平台、赌博游戏、线上赌场、彩票或博彩活动；\n" +
            "   - 是否推广赌博网站或提供赌博技术支持（如破解系统、投注建议等）。\n" +
            "6. **毒品及迷药相关内容**：\n" +
            "   - 发言中是否涉及毒品交易、使用、贩卖，或迷药（如迷奸药物）的推广；\n" +
            "   - 是否提供获取毒品或迷药的方式或渠道，或讨论毒品购买、运输、使用的细节。\n" +
            "7. **视觉混淆手段**：用户是否使用特殊符号、变体字、谐音字、特殊字符、拼音或emoji等方式试图绕过内容过滤？例如使用“曰结”代替“日结”，以及使用“橡木”代替“项目”，或“zhuāngyáng”代替“壮阳”。\n" +
            "8. **夸张致富承诺**：\n" +
            "   - 用户是否使用了不切实际或夸张的致富承诺，例如“踏实肯干几天破万”、“轻松几天买车买房”、“快速致富，无需经验”等；\n" +
            "   - 这种信息通常具有误导性，常见于非法投资、传销、虚假项目推广。\n" +
            "9. **网站分享**：\n" +
            "   - 用户分享的链接是否来自知名的正规网站（如github.com、google.com、youtube.com、twitter.com）？如果是，请谨慎判断是否为广告；\n" +
            "   - 如果链接来自不知名或可疑网站，尤其是涉及博彩、色情、毒品、虚拟货币交易、黑灰产等内容，需严格审查。\n" +
            "10. **发言长度与频率**：\n" +
            "   - 用户的发言是否过短且没有实质内容，或反复发送相同信息？\n" +
            "   - 频繁发送短信息（如链接或联系方式），通常表明具有广告或引流性质。\n" +
            "11. **隐蔽推销与暗语**：\n" +
            "   - 用户是否通过隐晦语言或模糊词语推销产品或服务，或暗示违法活动（如“搞钱”、“有项目”、“招人”等）。\n" +
            "12. **虚拟货币和投资陷阱**：\n" +
            "   - 是否涉及虚拟货币的非法交易、币圈诈骗、挖矿骗局等内容；\n" +
            "   - 是否推销不明来源的投资机会、快速致富计划，或承诺高额回报的投资。\n" +
            "13. **加入社交群组的诱导**：用户是否通过各种方式（如红包、承诺高回报、免费服务等）诱导他人加入任何社交平台的群组（如QQ群、微信群、Telegram群、Discord群等）？\n" +
            "14. **个人频道信息**：输入内容中若出现「个人频道」「频道简介」「频道置顶」等字段，说明这是该用户资料里绑定的个人频道，需要把这些频道的名称、简介、置顶内容一并当作其对外宣传来判定：\n" +
            "   - 频道名称或简介涉及代充值、代付、保底收益、日赚月入、稳定无风险、速度来人、拉群引流、博彩、色情、投资理财、刷单、招聘代理、卖号卖卡等，应判定为垃圾广告；\n" +
            "   - 典型例子：频道名为「代充值项目 每天保底500+」、简介写「长期稳定无风险，速度来人」，即使发言本身没有广告词，也应判定为垃圾广告；\n" +
            "   - 正常的游戏、资讯、个人博客类频道（不含上述特征）不应被判为广告。\n" +
            "15. **向群内管理机器人提问的排除项**：若输入内容里带有「其中的 @ 提及是在向本群管理机器人提问，不属于推广或引流」这类说明，说明其中的 @机器人 是用户向本群的管理/客服机器人提问或召唤，不是推广别人的机器人或服务；不得仅凭「@机器人 + 某个词」就判定为推广、引流或垃圾广告，必须结合剩余内容判断。\n" +
            "以下是用户的发言内容：\n%s\n" +
            "请根据以上问题逐一判断，并以如下JSON格式返回结果：\n" +
            "{\n" +
            "\"spamChance\": <垃圾广告的可能性，范围为\"0\"-\"10\">,\n" +
            "\"spamReason\": \"<你判断该发言是否为垃圾广告或违法内容的原因>\"\n" +
            "}\n" +
            "只输出合法 json，不要任何多余文本。";


    /**
     * 启动时打印当前生效的 AI 配置，便于排错（日志级别 root=warn，故用 warn 输出）。
     */
    @PostConstruct
    public void logApiConfig() {
        log.warn("AI 配置已加载 -> apiHost={}, model={}, 实际请求地址={}v1/chat/completions, thinking={}, reasoning_effort={}",
                BaseInfo.getOpenAiApiHost(), BaseInfo.getOpenAIModel(), BaseInfo.getOpenAiApiHost(),
                shouldSendThinking() ? "发送" : "不发送",
                StringUtils.hasText(BaseInfo.getOpenAiReasoningEffort()) ? BaseInfo.getOpenAiReasoningEffort() : "不发送");
    }

    /**
     * 是否发送 DeepSeek 专有的 {@code thinking} 参数。
     * <ul>
     *     <li>{@code on} → 总是发送；</li>
     *     <li>{@code off} → 从不发送；</li>
     *     <li>{@code auto}（默认）→ 仅当 apiHost 是 DeepSeek 官方端点时才发送，避免第三方接口因无法识别而报错。</li>
     * </ul>
     */
    private boolean shouldSendThinking() {
        String mode = BaseInfo.getOpenAiThinking();
        if ("on".equals(mode)) {
            return true;
        }
        if ("off".equals(mode)) {
            return false;
        }
        return BaseInfo.getOpenAiApiHost().toLowerCase().contains("deepseek");
    }

    /**
     * 统一处理「思考」控制，两套字段按需下发：
     * <ul>
     *     <li>{@code thinking} —— DeepSeek 专有（关闭思考）；</li>
     *     <li>{@code reasoning_effort} —— 其他 OpenAI 兼容网关（如 Command Code），
     *         配置 {@code off} 可完全关闭思考；留空则不发送，避免不支持该字段的网关报错。</li>
     * </ul>
     */
    private void applyReasoningControl(DeepSeekChatCompletion chatCompletion) {
        if (shouldSendThinking()) {
            chatCompletion.setThinking(Collections.singletonMap("type", "disabled"));
        }
        String effort = BaseInfo.getOpenAiReasoningEffort();
        if (StringUtils.hasText(effort)) {
            chatCompletion.setReasoningEffort(effort);
        }
    }

    private OpenAiClient createOpenAiClient() {
        List<String> list = BaseInfo.getOpenAIApiKey().toJavaList(String.class, JSONReader.Feature.SupportArrayToBean);

        return OpenAiClient.builder()
                .apiKey(list)
                .authInterceptor(new DynamicKeyOpenAiAuthInterceptor())
                .apiHost(BaseInfo.getOpenAiApiHost())
                .build();
    }

    public List<ChatChoice> getOpenAiAnalyzeResult(String text) {
        final int MAX_RETRIES = 5;
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                String content = String.format(ANALYSIS_TEMPLATE, truncateString(text, 200));
                Message message = Message.builder().role(Message.Role.SYSTEM).content(content).build();
                ChatCompletion chatCompletion = createChatCompletion(message);
                List<ChatChoice> choices = createOpenAiClient().chatCompletion(chatCompletion).getChoices();
                if (isEmptyResult(choices)) {
                    throw new IllegalStateException("AI返回内容为空");
                }
                return choices;
            } catch (Exception e) {
                attempt++;
                log.error("OpenAI分析失败，尝试次数：{}，原因：{}", attempt, e.getMessage(), e);
                if (attempt >= MAX_RETRIES) {
                    log.error("达到最大尝试次数，停止重试。");
                    return Collections.emptyList();  // 返回一个空的列表
                }
            }
        }

        return Collections.emptyList();  // 当达到最大尝试次数时返回空列表
    }

    /**
     * AI 客服：依据 system prompt 与多轮历史生成一条自然语言回复。
     * <p>
     * 与 {@link #getOpenAiAnalyzeResult(String)} 的区别：不要求 JSON 输出、不使用 JSON_OBJECT，
     * 并且只重试 2 次 + 指数退避（客服场景高频调用，激进重试会放大上游限流）。
     *
     * @param systemPrompt 由该群全部知识条目拼接而成的系统提示
     * @param history      按时间顺序排列的消息（含本轮用户消息）
     * @return 回复文本；失败时返回 null（调用方静默跳过，绝不影响群内其他功能）
     */
    public String getCustomerServiceReply(String systemPrompt, List<Message> history) {
        final int MAX_RETRIES = 2;
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                ChatCompletion chatCompletion = createCustomerServiceCompletion(systemPrompt, history);
                List<ChatChoice> choices = createOpenAiClient().chatCompletion(chatCompletion).getChoices();
                if (isEmptyResult(choices)) {
                    throw new IllegalStateException("AI客服返回内容为空");
                }
                return choices.get(0).getMessage().getContent();
            } catch (Exception e) {
                attempt++;
                log.error("AI客服回复失败，尝试次数：{}，原因：{}", attempt, e.getMessage(), e);
                if (attempt >= MAX_RETRIES) {
                    return null;
                }
                try {
                    // 退避重试，避免触发上游限流
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private ChatCompletion createCustomerServiceCompletion(String systemPrompt, List<Message> history) {
        DeepSeekChatCompletion chatCompletion = new DeepSeekChatCompletion();
        chatCompletion.setModel(BaseInfo.getOpenAIModel());
        chatCompletion.setMaxTokens(1200);
        chatCompletion.setTemperature(0.7);
        chatCompletion.setTopP(1.0);
        chatCompletion.setPresencePenalty(0);
        chatCompletion.setFrequencyPenalty(0);
        // 关闭思考模式：更快、更省 token（具体字段按接口地址/配置决定）
        applyReasoningControl(chatCompletion);

        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder().role(Message.Role.SYSTEM).content(systemPrompt).build());
        if (history != null) {
            messages.addAll(history);
        }
        chatCompletion.setMessages(messages);
        return chatCompletion;
    }

    private ChatCompletion createChatCompletion(Message message) {
        DeepSeekChatCompletion chatCompletion = new DeepSeekChatCompletion();
        chatCompletion.setModel(BaseInfo.getOpenAIModel());
        chatCompletion.setMaxTokens(1000);
        chatCompletion.setTemperature(0.2);
        chatCompletion.setTopP(1.0);
        chatCompletion.setPresencePenalty(0);
        chatCompletion.setFrequencyPenalty(0);
        // 关闭思考模式：更快、更省 token（具体字段按接口地址/配置决定）
        applyReasoningControl(chatCompletion);
        chatCompletion.setResponseFormat(
                ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_OBJECT.getName())
                        .build()
        );
        chatCompletion.setMessages(Collections.singletonList(message));
        return chatCompletion;
    }

    private boolean isEmptyResult(List<ChatChoice> choices) {
        if (choices == null || choices.isEmpty() || choices.get(0).getMessage() == null) {
            return true;
        }

        String responseContent = choices.get(0).getMessage().getContent();
        return responseContent == null || responseContent.trim().isEmpty();
    }

    private String truncateString(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }
}
