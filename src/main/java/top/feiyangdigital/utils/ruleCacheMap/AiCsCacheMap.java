package top.feiyangdigital.utils.ruleCacheMap;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 客服面板的「等待输入」状态缓存。
 * <p>
 * 与 AddRuleCacheMap / DeleteRuleCacheMap 同思路：用内存 flag 表示管理员当前处于哪种输入状态，
 * 真正的文本输入由 CommonFunction 的消息分支按 flag 分发处理。
 * <p>
 * 群组归属（userId → groupId/groupName）仍复用 {@link AddRuleCacheMap}，这里只存状态。
 * <p>
 * flag 取值：
 * <ul>
 *     <li>{@code addPrompt} —— 等待输入知识条目</li>
 *     <li>{@code setKeyword} —— 等待输入关键词</li>
 *     <li>{@code notallow} —— 空闲</li>
 * </ul>
 *
 * @author Cnievana
 */
@Component
public class AiCsCacheMap {

    private final Map<String, String> userToFlagMap = new ConcurrentHashMap<>();

    public void updateFlag(String userId, String flag) {
        if (flag == null) {
            userToFlagMap.remove(userId);
        } else {
            userToFlagMap.put(userId, flag);
        }
    }

    public String getFlagForUser(String userId) {
        return userToFlagMap.get(userId);
    }

    public void clearFlag(String userId) {
        userToFlagMap.remove(userId);
    }
}
