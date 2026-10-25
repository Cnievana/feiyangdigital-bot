package top.feiyangdigital.sqlService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.feiyangdigital.entity.GroupInfo;
import top.feiyangdigital.entity.GroupInfoExample;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.mapper.GroupInfoMapper;

import java.util.List;

@Service
@Transactional
public class GroupInfoService {

    /** 面板设置有效期：15 分钟 */
    public static final long SETTING_TIMEOUT_MS = 15 * 60 * 1000L;

    @Autowired
    private GroupInfoMapper groupInfoMapper;

    @CacheEvict(value = "groupinfo", key = "#record.groupid")
    public boolean addGroup(GroupInfoWithBLOBs record) {
        return groupInfoMapper.insertSelective(record) > 0;
    }

    @Transactional(propagation = Propagation.NEVER)
    public boolean selGroup(String chatId) {
        GroupInfoExample example = new GroupInfoExample();
        GroupInfoExample.Criteria criteria = example.createCriteria();
        criteria.andGroupidEqualTo(chatId);
        if (groupInfoMapper.countByExample(example) > 0) {
            return false;
        }
        return true;
    }

    @CacheEvict(value = "groupinfo", key = "#chatId")
    public boolean updateSelectiveByChatId(GroupInfoWithBLOBs groupInfoWithBLOBs, String chatId) {
        GroupInfoExample example = new GroupInfoExample();
        GroupInfoExample.Criteria criteria = example.createCriteria();
        criteria.andGroupidEqualTo(chatId);
        return groupInfoMapper.updateByExampleSelective(groupInfoWithBLOBs, example) > 0;
    }

    @Cacheable(value = "groupinfo", key = "#groupId")
    @Transactional(propagation = Propagation.NEVER)
    public GroupInfoWithBLOBs selAllByGroupId(String groupId){
        GroupInfoExample example = new GroupInfoExample();
        GroupInfoExample.Criteria criteria = example.createCriteria();
        criteria.andGroupidEqualTo(groupId);
        List<GroupInfoWithBLOBs> list = groupInfoMapper.selectByExampleWithBLOBs(example);
        if (!list.isEmpty()){
            return list.get(0);
        }
        return null;
    }

    /** 读取「AI进群昵称/简介检测」开关，未设置时默认 close。 */
    @Transactional(propagation = Propagation.NEVER)
    public String selIntoGroupAiCheckFlag(String groupId){
        String flag = groupInfoMapper.selectIntoGroupAiCheckFlag(groupId);
        return flag == null ? "close" : flag;
    }

    /** 设置「AI进群昵称/简介检测」开关，返回是否成功。 */
    public boolean updateIntoGroupAiCheckFlag(String groupId, String flag){
        return groupInfoMapper.updateIntoGroupAiCheckFlag(groupId, flag) > 0;
    }

    /**
     * 读取「AI违规自动禁言」开关，未设置时默认 close。
     * 与 aiCs* 系列一致：不进 groupinfo 缓存，每次直读（groupId 有索引）。
     */
    @Transactional(propagation = Propagation.NEVER)
    public String selAiMuteFlag(String groupId){
        String flag = groupInfoMapper.selectAiMuteFlag(groupId);
        return flag == null ? "close" : flag;
    }

    /** 设置「AI违规自动禁言」开关，返回是否成功。 */
    public boolean updateAiMuteFlag(String groupId, String flag){
        return groupInfoMapper.updateAiMuteFlag(groupId, flag) > 0;
    }

    /**
     * 查询该管理员（群主 / 匿名管理员）名下的所有群组，用于私聊 /start 的群组列表。
     */
    @Transactional(propagation = Propagation.NEVER)
    public List<GroupInfo> selGroupsManagedBy(String userId) {
        return groupInfoMapper.selectGroupsManagedBy(userId);
    }

    /**
     * 判断该群的「面板设置有效期」是否已超时（阈值 {@link #SETTING_TIMEOUT_MS}）。
     * <p>
     * 与项目原有逻辑（AddAutoReplyRule / DeleteSingleRuleByKeyWord）保持一致：
     * 时间戳为空或格式异常时**不判定为超时**，避免误提示。
     */
    @Transactional(propagation = Propagation.NEVER)
    public boolean isSettingExpired(String groupId) {
        return isSettingExpired(selAllByGroupId(groupId));
    }

    /**
     * 纯判断，不查库：调用方若已持有实体（如面板按钮点击处），可直接复用，避免重复查询。
     */
    public static boolean isSettingExpired(GroupInfoWithBLOBs groupInfo) {
        if (groupInfo == null) {
            return false;
        }
        String settingTimestamp = groupInfo.getSettingtimestamp();
        if (settingTimestamp == null || settingTimestamp.isEmpty()) {
            return false;
        }
        try {
            return System.currentTimeMillis() - Long.parseLong(settingTimestamp) > SETTING_TIMEOUT_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== AI 客服（aiCs）相关配置 ====================
    // 这些字段未纳入缓存的实体，故每次直接读库（groupId 上有索引，开销可忽略）。

    /** 读取「AI客服」总开关，未设置时默认 close。 */
    @Transactional(propagation = Propagation.NEVER)
    public String selAiCsFlag(String groupId){
        String flag = groupInfoMapper.selectAiCsFlag(groupId);
        return flag == null ? "close" : flag;
    }

    public boolean updateAiCsFlag(String groupId, String flag){
        return groupInfoMapper.updateAiCsFlag(groupId, flag) > 0;
    }

    /** 读取 AI客服群内触发方式，未设置时默认 at（仅@bot时回复）。 */
    @Transactional(propagation = Propagation.NEVER)
    public String selAiCsTriggerMode(String groupId){
        String mode = groupInfoMapper.selectAiCsTriggerMode(groupId);
        return (mode == null || mode.isEmpty()) ? "at" : mode;
    }

    public boolean updateAiCsTriggerMode(String groupId, String mode){
        return groupInfoMapper.updateAiCsTriggerMode(groupId, mode) > 0;
    }

    /** 读取 AI客服关键词（keyword 触发方式下使用的正则），未设置时返回 null。 */
    @Transactional(propagation = Propagation.NEVER)
    public String selAiCsKeyword(String groupId){
        return groupInfoMapper.selectAiCsKeyword(groupId);
    }

    public boolean updateAiCsKeyword(String groupId, String keyword){
        return groupInfoMapper.updateAiCsKeyword(groupId, keyword) > 0;
    }

    /** 读取「私聊AI客服」开关，未设置时默认 close。 */
    @Transactional(propagation = Propagation.NEVER)
    public String selAiCsPrivateFlag(String groupId){
        String flag = groupInfoMapper.selectAiCsPrivateFlag(groupId);
        return flag == null ? "close" : flag;
    }

    public boolean updateAiCsPrivateFlag(String groupId, String flag){
        return groupInfoMapper.updateAiCsPrivateFlag(groupId, flag) > 0;
    }

    /**
     * 反查该管理员名下「已开启总开关 + 已开启私聊客服」的群。
     * <p>
     * 用于容器重启后恢复私聊绑定：内存里的 userId→groupId 映射会随重启清空，
     * 此时直接查库把管理员身份认回来，避免「必须重新点一次 🤖Bot设置 才能用私聊客服」。
     *
     * @return 匹配到的群 ID；没有则返回 null
     */
    @Transactional(propagation = Propagation.NEVER)
    public String selAiCsPrivateGroupId(String userId){
        return groupInfoMapper.selectAiCsPrivateGroupId(userId);
    }

}