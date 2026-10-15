package top.feiyangdigital.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

public class BaseInfo {
    private static final String CONFIG_PATH = String.valueOf(Paths.get("").toAbsolutePath().resolve("config.json"));
    private static final JSONObject CONFIG;

    static {
        try {
            CONFIG = readJson(CONFIG_PATH);
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败 " + CONFIG_PATH, e);
        }
    }

    public static JSONObject readJson(String filePath) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(filePath);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            return JSON.parseObject(sb.toString());
        }
    }

    public static String getConfigValue(String key, String subKey) {
        JSONObject subConfig = CONFIG.getJSONObject(key);
        return (subConfig != null) ? subConfig.getString(subKey) : null;
    }

    public static JSONObject getConfig(String key){
        return CONFIG.getJSONObject(key);
    }

    public static String getBotLimitStatus(){
        return getConfigValue("groupLimit", "status");
    }

    public static JSONArray getOpenAIApiKey(){
        return CONFIG.getJSONArray("openAIApiKey");
    }

    /**
     * AI 接口地址（OpenAI 兼容 base url，**必须带结尾斜杠**）。
     * <p>
     * 取值优先级：环境变量 {@code OPENAI_API_HOST} → config.json 的 {@code openAIApiHost} → DeepSeek 官方。
     * 客户端会在该地址后拼接 {@code v1/chat/completions}。
     */
    public static String getOpenAiApiHost() {
        String envHost = System.getenv("OPENAI_API_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            return envHost.trim();
        }

        String configHost = CONFIG.getString("openAIApiHost");
        if (configHost != null && !configHost.trim().isEmpty()) {
            return configHost.trim();
        }

        return "https://api.deepseek.com/";
    }

    /**
     * 是否向接口发送 DeepSeek 专有的 {@code thinking} 参数。
     * <p>
     * 取值：{@code on} 总是发送 / {@code off} 从不发送 / {@code auto}（默认）仅当接口地址是 DeepSeek 时发送。
     * 之所以要能关掉，是因为非 DeepSeek 的兼容接口可能因无法识别该字段而报错。
     */
    public static String getOpenAiThinking() {
        String mode = CONFIG.getString("openAIThinking");
        return (mode == null || mode.trim().isEmpty()) ? "auto" : mode.trim().toLowerCase();
    }

    /**
     * 推理强度（reasoning effort），控制模型「思考」的力度。
     * <p>
     * 取值：{@code off} / {@code low} / {@code medium} / {@code high} / {@code xhigh} / {@code max}；
     * **留空表示不发送该参数**（不同服务端支持的取值不同，以服务端返回的错误提示为准）。
     * <p>
     * 优先级：环境变量 {@code OPENAI_REASONING_EFFORT} → config.json 的 {@code openAIReasoningEffort}。
     */
    public static String getOpenAiReasoningEffort() {
        String envEffort = System.getenv("OPENAI_REASONING_EFFORT");
        if (envEffort != null && !envEffort.trim().isEmpty()) {
            return envEffort.trim().toLowerCase();
        }

        String configEffort = CONFIG.getString("openAIReasoningEffort");
        return (configEffort == null || configEffort.trim().isEmpty()) ? "" : configEffort.trim().toLowerCase();
    }

    public static String getOpenAIModel() {
        String envModel = System.getenv("DEEPSEEK_MODEL");
        if (envModel != null && !envModel.trim().isEmpty()) {
            return envModel.trim();
        }

        String configModel = CONFIG.getString("openAIModel");
        if (configModel != null && !configModel.trim().isEmpty()) {
            return configModel.trim();
        }

        return "deepseek-v4-flash";
    }

    public static String getApiServer(){
        return CONFIG.getString("api-server");
    }

    public static JSONObject getGoogleServiceAccountConfig(){
        return getConfig("googleServiceAccount");
    }

    public static List<String> getGroupWhiteList(){
       JSONObject groupLimit = getConfig("groupLimit");
       JSONArray whiteList = groupLimit.getJSONArray("whiteList");
       return whiteList.toJavaList(String.class, JSONReader.Feature.SupportArrayToBean);
    }

    public static String getBotMode(){
        return getConfigValue("botConfig", "mode");
    }

    public static String getBotName() {
        return getConfigValue("botConfig", "name");
    }

    public static String getBotToken() {
        return getConfigValue("botConfig", "token");
    }

    public static String getBotPath() {
        return getConfigValue("botConfig","path");
    }

}
