package top.feiyangdigital.utils;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 客服的内存多轮对话上下文。
 * <p>
 * ⚠️ 只存内存，重启即清空（与需求一致）。
 * 为避免内存无界增长，做了三层保护：
 * <ol>
 *     <li>每个会话只保留最近 {@link #MAX_HISTORY} 条消息（user/assistant 合计）；</li>
 *     <li>会话闲置超过 {@link #TTL_MS} 时惰性失效；</li>
 *     <li>会话总数超过 {@link #MAX_SESSIONS} 时触发一次主动清理。</li>
 * </ol>
 *
 * @author Cnievana
 */
@Component
public class AiCsConversationCache {

    /** 一轮对话中的单条记录 */
    public static class Turn {
        private final String role;
        private final String content;

        public Turn(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    private static class Session {
        final Deque<Turn> turns = new ArrayDeque<>();
        volatile long lastActive;
    }

    /** 每个会话保留的历史条数（user + assistant 合计，8 条 = 最近 4 轮） */
    private static final int MAX_HISTORY = 8;
    /** 会话闲置超时：30 分钟 */
    private static final long TTL_MS = 30 * 60 * 1000;
    /** 会话数上限，超过后触发清理 */
    private static final int MAX_SESSIONS = 5000;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 会话 key：群聊与私聊、不同用户之间互相隔离。 */
    public static String buildKey(String chatId, String userId) {
        return chatId + "_" + userId;
    }

    /** 取历史（返回副本，惰性清理过期会话）。 */
    public List<Turn> getHistory(String key) {
        Session session = sessions.get(key);
        if (session == null) {
            return Collections.emptyList();
        }
        if (System.currentTimeMillis() - session.lastActive > TTL_MS) {
            sessions.remove(key);
            return Collections.emptyList();
        }
        synchronized (session) {
            return new ArrayList<>(session.turns);
        }
    }

    /** 成功回复后追加一轮对话。 */
    public void append(String key, String userContent, String assistantContent) {
        if (userContent == null || assistantContent == null) {
            return;
        }
        Session session = sessions.computeIfAbsent(key, k -> new Session());
        synchronized (session) {
            session.turns.addLast(new Turn("user", userContent));
            session.turns.addLast(new Turn("assistant", assistantContent));
            while (session.turns.size() > MAX_HISTORY) {
                session.turns.removeFirst();
            }
            session.lastActive = System.currentTimeMillis();
        }
        if (sessions.size() > MAX_SESSIONS) {
            cleanUp();
        }
    }

    public void clear(String key) {
        sessions.remove(key);
    }

    /** 清理过期会话，防止内存无界增长。 */
    public void cleanUp() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now - entry.getValue().lastActive > TTL_MS);
    }

    /** 当前会话数（供运维观察）。 */
    public int size() {
        return sessions.size();
    }
}
