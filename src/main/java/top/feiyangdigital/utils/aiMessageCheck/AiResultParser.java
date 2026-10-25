package top.feiyangdigital.utils.aiMessageCheck;

import com.alibaba.fastjson2.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 返回内容解析器（容错版）。
 * <p>
 * 背景（2026-10-24 线上日志实测）：模型偶尔会把 JSON 包在 Markdown 代码块里，
 * 或让字符串值里出现裸换行、控制字符、被截断的 emoji（孤立代理对），
 * 此时 fastjson2 读取字符串会抛异常（JSONReaderUTF16.readString），
 * 原实现 catch 后直接 return，等于把这条广告放过去了。
 * <p>
 * 本类做三件事：
 * <ol>
 *     <li>去掉代码块标记并截取第一个 <code>{</code> 到最后一个 <code>}</code>；</li>
 *     <li>清理非法控制字符与孤立代理字符；</li>
 *     <li>即使整体 JSON 仍解析失败，也用正则兜底提取 spamChance，
 *         保证「是不是广告」的主结论不会因为排版问题丢失。</li>
 * </ol>
 */
public final class AiResultParser {

    private static final Pattern SPAM_CHANCE_PATTERN =
            Pattern.compile("\"spamChance\"\\s*:\\s*\"?\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAM_REASON_PATTERN =
            Pattern.compile("\"spamReason\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    private AiResultParser() {
    }

    /** 解析结果：spamChance 一定非空，spamReason 可能为空串。 */
    public static final class SpamResult {
        private final Integer spamChance;
        private final String spamReason;

        SpamResult(Integer spamChance, String spamReason) {
            this.spamChance = spamChance;
            this.spamReason = spamReason;
        }

        public Integer getSpamChance() {
            return spamChance;
        }

        public String getSpamReason() {
            return spamReason == null ? "" : spamReason;
        }
    }

    /**
     * 解析 AI 返回的检测结果。
     *
     * @return 连 spamChance 都提取不到时返回 null（调用方据此跳过本次检测）
     */
    public static SpamResult parseSpam(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        JSONObject json = parseObject(raw);
        Integer chance = json == null ? null : json.getInteger("spamChance");
        String reason = json == null ? null : json.getString("spamReason");
        if (chance == null) {
            chance = regexChance(raw);
        }
        if (reason == null || reason.isEmpty()) {
            reason = regexReason(raw);
        }
        if (chance == null) {
            return null;
        }
        return new SpamResult(chance, reason);
    }

    /** 尽量从原始文本里解析出 JSON 对象，失败返回 null（不抛异常）。 */
    public static JSONObject parseObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = stripCodeFence(raw.trim());
        String sliced = sliceJsonObject(text);
        if (sliced == null) {
            return null;
        }
        String cleaned = stripIllegalChars(sliced);
        try {
            return JSONObject.parseObject(cleaned);
        } catch (Exception ignored) {
            // 模型偶尔在字符串值里输出裸换行/制表符，替换为空格后再试一次
            try {
                return JSONObject.parseObject(cleaned.replace('\r', ' ').replace('\n', ' ').replace('\t', ' '));
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    /** 去掉 ```json ... ``` 包裹。 */
    private static String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstLineEnd > 0 && lastFence > firstLineEnd) {
            return text.substring(firstLineEnd + 1, lastFence).trim();
        }
        return text;
    }

    /** 截取第一个 { 到最后一个 }，去掉模型在前后多写的解释性文字。 */
    private static String sliceJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * 清理会让 fastjson2 读取字符串失败的字符：
     * 孤立代理对（emoji 被 substring 截断）、除 \n \r \t 之外的控制字符。
     */
    private static String stripIllegalChars(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    sb.append(c).append(text.charAt(i + 1));
                    i++;
                }
                // 孤立高代理：丢弃
            } else if (Character.isLowSurrogate(c)) {
                // 孤立低代理：丢弃
            } else if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Integer regexChance(String raw) {
        Matcher matcher = SPAM_CHANCE_PATTERN.matcher(raw);
        if (matcher.find()) {
            try {
                return Integer.valueOf(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String regexReason(String raw) {
        Matcher matcher = SPAM_REASON_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).replace("\\n", " ").replace("\\\"", "\"");
        }
        return "";
    }
}
