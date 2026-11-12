package top.feiyangdigital.utils.userProfile;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析用户资料里 t.me 邀请链接所指向的群，取其「群名 / 成员数 / 简介」。
 * <p>
 * 背景：{@code t.me/+hash}、{@code t.me/joinchat/hash} 只是 Telegram 的链接格式，
 * 正常用户拉人进自己的群、分享资源群都会用，<b>链接本身不能作为广告判据</b>；
 * 真正有意义的是「链接指向的那个群是什么内容」（例如群名是「公群388 万盛集团京东/淘宝刷销」、
 * 简介是一天收入999+ 的代刷招人）。
 * <p>
 * Bot API 的 {@code getChat} 只接受数字 chat_id 或 {@code @username}，无法解析邀请 hash；
 * 本类改为抓取 Telegram 官方的 {@code https://t.me/+hash} 预览页，
 * 从 {@code tgme_page_title / tgme_page_extra / tgme_page_description} 三个节点取信息，
 * 与客户端点击链接时弹出的预览卡是同一份数据。
 * <p>
 * 安全与稳定性：
 * <ul>
 *     <li>只请求 {@code t.me} 固定域名，hash 侧用白名单正则，杜绝 SSRF；</li>
 *     <li>连接 3s / 读取 4s / 总 6s 超时，失败一律返回 null，绝不影响进群主流程；</li>
 *     <li>结果按 hash 缓存 10 分钟：一波广告号通常投放同一个群，可显著减少外部请求；</li>
 *     <li>群关闭预览或私密群不返回简介时，只输出能取到的字段，不做补偿。</li>
 * </ul>
 */
@Slf4j
@Component
public class InviteLinkResolver {

    /** t.me/+hash 或 t.me/joinchat/hash；hash 允许字母数字下划线与短横线。 */
    private static final Pattern INVITE_PATTERN = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?t\\.me/(?:\\+|joinchat/)([A-Za-z0-9_-]{8,})");

    /** 单份资料最多解析的链接数，避免资料里塞满链接拖慢进群流程。 */
    private static final int MAX_LINKS = 2;

    /** 缓存有效期：同一群被反复投放时可复用解析结果。 */
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    /** 缓存条数上限，防止长期运行内存膨胀。 */
    private static final int CACHE_MAX_SIZE = 500;

    /** 单个字段的输出上限，避免超长简介挤占 AI 输入预算。 */
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_EXTRA_LENGTH = 60;
    private static final int MAX_DESCRIPTION_LENGTH = 320;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 从若干段文本中找出邀请链接并解析出群信息。
     *
     * @param texts 待扫描文本（个人简介、个人频道画像等）
     * @return 可直接拼进 AI 输入的说明段落；没有链接或全部解析失败时返回空串
     */
    public String resolve(String... texts) {
        Set<String> hashes = collectHashes(texts);
        if (hashes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (String hash : hashes) {
            index++;
            if (index > MAX_LINKS) {
                break;
            }
            GroupPreview preview = getPreview(hash);
            if (preview == null || !preview.hasAnyContent()) {
                log.warn("邀请链接解析失败或无可读信息 hash={}", hash);
                continue;
            }
            sb.append("链接 ").append(index).append("：https://t.me/+").append(hash).append("\n");
            if (StringUtils.hasText(preview.title)) {
                sb.append("  群名：").append(preview.title).append("\n");
            }
            if (StringUtils.hasText(preview.extra)) {
                sb.append("  成员数：").append(preview.extra).append("\n");
            }
            if (StringUtils.hasText(preview.description)) {
                // 简介内部的换行压成 " / "，保持一行一个字段，便于模型阅读
                sb.append("  群简介：").append(preview.description.replace("\n", " / ")).append("\n");
            }
        }
        if (sb.length() == 0) {
            return "";
        }
        return "【简介中的邀请链接指向的群（由链接预览页解析，供判断该链接是否为引流）】\n" + sb;
    }

    private Set<String> collectHashes(String... texts) {
        Set<String> hashes = new LinkedHashSet<>();
        if (texts == null) {
            return hashes;
        }
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Matcher matcher = INVITE_PATTERN.matcher(text);
            while (matcher.find()) {
                hashes.add(matcher.group(1));
            }
        }
        return hashes;
    }

    /** 带 TTL 的缓存读取；拿不到时返回 null。 */
    private GroupPreview getPreview(String hash) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(hash);
        if (entry != null && now - entry.createdAt < CACHE_TTL_MS) {
            return entry.preview;
        }
        GroupPreview preview = fetch(hash);
        if (preview != null && preview.hasAnyContent()) {
            if (cache.size() > CACHE_MAX_SIZE) {
                cache.entrySet().removeIf(e -> now - e.getValue().createdAt >= CACHE_TTL_MS);
            }
            cache.put(hash, new CacheEntry(preview, now));
        }
        return preview;
    }

    /** 抓取预览页并解析三个节点。 */
    private GroupPreview fetch(String hash) {
        // 实测（2026-11-12，服务器直连）：
        //   https://t.me/+hash?embed=1&mode=tme  -> 4.8KB 精简 widget，<b>没有</b> tgme_page_* 节点与 og 元信息；
        //   https://t.me/+hash                   -> 13KB 标准预览页，群名/成员数/简介齐全。
        // 因此直接请求标准预览页，仅在没有内容时才用 embed 版兜底。
        GroupPreview preview = fetchOnce("https://t.me/+" + hash, hash);
        if (preview == null || !preview.hasAnyContent()) {
            preview = fetchOnce("https://t.me/+" + hash + "?embed=1&mode=tme", hash);
        }
        return preview;
    }

    private GroupPreview fetchOnce(String url, String hash) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("邀请链接预览页请求失败 hash={}, code={}", hash, response.code());
                return null;
            }
            String html = response.body().string();
            GroupPreview preview = new GroupPreview();
            preview.title = truncate(cleanText(extractByClass(html, "tgme_page_title")), MAX_TITLE_LENGTH);
            preview.extra = truncate(cleanText(extractByClass(html, "tgme_page_extra")), MAX_EXTRA_LENGTH);
            preview.description = truncate(cleanText(extractByClass(html, "tgme_page_description")), MAX_DESCRIPTION_LENGTH);

            // 兜底：部分页面只输出 og 元信息
            if (!StringUtils.hasText(preview.title)) {
                String ogTitle = cleanText(metaContent(html, "og:title"));
                // "Telegram: Contact @xxx" / "Telegram: Join Group Chat" 这类通用标题没有信息量
                if (StringUtils.hasText(ogTitle) && !ogTitle.toLowerCase().startsWith("telegram:")) {
                    preview.title = truncate(ogTitle, MAX_TITLE_LENGTH);
                }
            }
            if (!StringUtils.hasText(preview.description)) {
                String ogDesc = cleanText(metaContent(html, "og:description"));
                if (StringUtils.hasText(ogDesc)) {
                    // og:description 常形如 "14 289 members, 1 234 online"：
                    // 含 members 时当作成员数，否则当作简介
                    if (ogDesc.toLowerCase().contains("member")) {
                        if (!StringUtils.hasText(preview.extra)) {
                            preview.extra = truncate(ogDesc, MAX_EXTRA_LENGTH);
                        }
                    } else {
                        preview.description = truncate(ogDesc, MAX_DESCRIPTION_LENGTH);
                    }
                }
            }
            log.warn("邀请链接解析完成 hash={}, 群名={}, 成员={}, 简介长度={}", hash,
                    preview.title, preview.extra,
                    preview.description == null ? 0 : preview.description.length());
            return preview;
        } catch (IOException | RuntimeException e) {
            log.warn("邀请链接预览页解析异常 hash={}, err={}", hash, e.getMessage());
            return null;
        }
    }

    /**
     * 取指定 class 的 div 内部 HTML。
     * <p>
     * 不引入 HTML 解析库（项目没有 jsoup），改为按 class 定位后做标签配对扫描，
     * 避免简介内出现嵌套标签时被非贪婪正则提前截断。
     */
    private String extractByClass(String html, String className) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        int classIdx = html.indexOf("class=\"" + className);
        if (classIdx < 0) {
            return "";
        }
        // 从该标签的起始 '<' 开始，定位属性结束的 '>'
        int contentStart = html.indexOf('>', classIdx);
        if (contentStart < 0) {
            return "";
        }
        contentStart++;
        // 配对扫描：统计 div 开合，找到与起始 div 对应的闭合标签
        int depth = 1;
        int cursor = contentStart;
        while (cursor < html.length() && depth > 0) {
            int nextOpen = html.indexOf("<div", cursor);
            int nextClose = html.indexOf("</div>", cursor);
            if (nextClose < 0) {
                break;
            }
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                cursor = nextOpen + 4;
            } else {
                depth--;
                if (depth == 0) {
                    return html.substring(contentStart, nextClose);
                }
                cursor = nextClose + 6;
            }
        }
        return "";
    }

    /** 取 meta 标签的 content 属性。 */
    private String metaContent(String html, String property) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        Pattern p = Pattern.compile(
                "<meta[^>]+(?:property|name)=\"" + Pattern.quote(property) + "\"[^>]*content=\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        // content 在 property 之前的写法
        Pattern p2 = Pattern.compile(
                "<meta[^>]+content=\"([^\"]*)\"[^>]*(?:property|name)=\"" + Pattern.quote(property) + "\"",
                Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(html);
        return m2.find() ? m2.group(1) : "";
    }

    /** 去标签、把 br 转成换行、压缩空白、解码常见实体。 */
    private String cleanText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?s)<[^>]+>", "");
        text = decodeEntities(text);
        text = text.replace('\u00a0', ' ');
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{2,}", "\n");
        return text.trim();
    }

    private String decodeEntities(String text) {
        String s = text;
        s = s.replace("&nbsp;", " ");
        s = s.replace("&lt;", "<");
        s = s.replace("&gt;", ">");
        s = s.replace("&quot;", "\"");
        s = s.replace("&apos;", "'");
        s = s.replace("&#39;", "'");
        s = s.replace("&amp;", "&");
        // 数字实体
        Matcher m = Pattern.compile("&#(\\d{1,6});").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int code = Integer.parseInt(m.group(1));
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) code)));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "...";
    }

    /** 解析结果。 */
    private static final class GroupPreview {
        private String title;
        private String extra;
        private String description;

        private boolean hasAnyContent() {
            return StringUtils.hasText(title) || StringUtils.hasText(extra) || StringUtils.hasText(description);
        }
    }

    /** 缓存项：记录写入时间用于 TTL 判断。 */
    private static final class CacheEntry {
        private final GroupPreview preview;
        private final long createdAt;

        private CacheEntry(GroupPreview preview, long createdAt) {
            this.preview = preview;
            this.createdAt = createdAt;
        }
    }
}
