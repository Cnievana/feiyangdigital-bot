package top.feiyangdigital.sqlService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.feiyangdigital.entity.GroupInfoExample;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.mapper.GroupInfoMapper;

import java.util.List;

@Service
@Transactional
public class GroupInfoService {

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

}