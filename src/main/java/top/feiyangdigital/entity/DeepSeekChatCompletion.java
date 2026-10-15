package top.feiyangdigital.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;

import java.util.Map;

/**
 * 扩展 chatgpt-java 的 {@link ChatCompletion}，加入两个非 OpenAI 标准、但项目需要的参数：
 * <ul>
 *     <li>{@code thinking} —— DeepSeek 专有，用于关闭思考模式；</li>
 *     <li>{@code reasoning_effort} —— 部分 OpenAI 兼容网关（如 Command Code）用它控制思考力度。</li>
 * </ul>
 * 两个字段均为 null 时不会出现在请求体中（基类带 {@code @JsonInclude(NON_NULL)}）。
 */
public class DeepSeekChatCompletion extends ChatCompletion {

    private Map<String, String> thinking;

    /**
     * 推理强度：{@code off} / {@code low} / {@code medium} / {@code high} / {@code xhigh} / {@code max}
     * （具体支持哪些取值由服务端决定；{@code off} 表示完全关闭思考）。
     */
    @JsonProperty("reasoning_effort")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reasoningEffort;

    public Map<String, String> getThinking() {
        return thinking;
    }

    public void setThinking(Map<String, String> thinking) {
        this.thinking = thinking;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }
}
