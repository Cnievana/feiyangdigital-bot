package top.feiyangdigital.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KeywordsFormat {
    private String uuid;
    private String regex;
    private String replyText;

    private List<String> keywordsButtons = new ArrayList<>();
    private String photoUrl;
    private String videoUrl;

    private Map<String, String> ruleMap = new ConcurrentHashMap<>();

    public KeywordsFormat(){

    }

    public KeywordsFormat(String data) {
        String[] keyValuePair = data.split(" \\| ");
        this.uuid = keyValuePair[0];

        String[] parts = keyValuePair[1].split("===");
        this.regex = parts[0];
        String[] actions = parts[1].split("&&");
        this.replyText = actions[0];
        for (String action : actions) {
            if (action.startsWith("btns=")) {
                String[] btnParts = action.substring(5).split("\\$\\$\\$");
                keywordsButtons.addAll(Arrays.asList(btnParts));
            } else if (action.startsWith("photo=")) {
                this.photoUrl = action.substring(6);
            } else if (action.startsWith("video=")) {
                this.videoUrl = action.substring(6);
            } else if (action.startsWith("botBan=")) {
                // botBan=：只对「机器人 / 通过机器人发送」的群聊正文生效，命中即删消息 + 封禁。
                // 不需要回复文案，也不向群里发通知；BotOnly 标记让 MessageHandle 在编译正则之前
                // 就把非 bot 消息跳过，普通用户零误伤。
                ruleMap.put("BotOnly", "true");
                ruleMap.put("BotBanAfterXSeconds", action.substring(7).split("、")[0].split("=")[1]);
            } else if (action.startsWith("del=")) {
                ruleMap.put("DeleteAfterXSeconds", action.substring(4).split("、")[0].split("=")[1]);
                ruleMap.put("DeleteReplyAfterYSeconds", action.substring(4).split("、")[1].split("=")[1]);
            } else if (action.startsWith("welcome=")){
                ruleMap.put("DelWelcome",action.substring(8));
            } else if (action.startsWith("intoGroupBan=")) {
                ruleMap.put("DelIntoGroupBan",action.substring(13));
            } else if (action.startsWith("crontab=")) {
                ruleMap.put("crontabOption",action.substring(8));
            }
        }
        // botBan 规则不需要回复文案。为空时补一个纯展示文案，
        // 否则「规则添加成功」的回显消息会因文本为空被 Telegram 拒绝（400 message text is empty）。
        if ((this.replyText == null || this.replyText.trim().isEmpty()) && ruleMap.containsKey("BotOnly")) {
            this.replyText = "【广告机器人封禁规则】只对机器人发送的正文生效，命中即删除消息并封禁，不发送群内通知。";
        }
    }

    // Getter methods...

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getUuid() {
        return uuid;
    }

    public String getRegex() {
        return regex;
    }

    public String getReplyText() {
        return replyText;
    }

    public List<String> getKeywordsButtons() {
        return keywordsButtons;
    }

    public Map<String, String> getRuleMap() {
        return ruleMap;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }

    public void setKeywordsButtons(List<String> keywordsButtons) {
        this.keywordsButtons = keywordsButtons;
    }

    public void setRuleMap(Map<String, String> map) {
        this.ruleMap = map;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    @Override
    public String toString() {
        return "KeywordsFormat{" +
                "uuid='" + uuid + '\'' +
                ", regex='" + regex + '\'' +
                ", replyText='" + replyText + '\'' +
                ", keywordsButtons=" + keywordsButtons +
                ", photoUrl='" + photoUrl + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", map=" + ruleMap +
                '}';
    }
}
