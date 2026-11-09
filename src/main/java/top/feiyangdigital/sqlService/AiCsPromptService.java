package top.feiyangdigital.sqlService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.feiyangdigital.entity.AiCsPrompt;
import top.feiyangdigital.mapper.AiCsPromptMapper;

import java.util.Collections;
import java.util.List;

/**
 * AI 客服知识条目的读写入口。
 * <p>
 * ⚠️ ehcache.xml 中所有 cache 都是 eternal=true（永不过期），
 * 所以增删必须成对 evict，否则面板改完提示词不会生效。
 *
 * @author Cnievana
 */
@Service
@Transactional
public class AiCsPromptService {

    @Autowired
    private AiCsPromptMapper aiCsPromptMapper;

    /** 读取某群全部条目（带缓存）。 */
    @Cacheable(value = "aiCsPrompt", key = "#groupId")
    @Transactional(propagation = Propagation.NEVER)
    public List<AiCsPrompt> selByGroupId(String groupId) {
        List<AiCsPrompt> list = aiCsPromptMapper.selectByGroupId(groupId);
        return list == null ? Collections.emptyList() : list;
    }

    @Transactional(propagation = Propagation.NEVER)
    public int countByGroupId(String groupId) {
        return aiCsPromptMapper.countByGroupId(groupId);
    }

    @Transactional(propagation = Propagation.NEVER)
    public AiCsPrompt selByIdAndGroupId(Integer id, String groupId) {
        return aiCsPromptMapper.selectByIdAndGroupId(id, groupId);
    }

    /** 新增条目并清缓存。 */
    @CacheEvict(value = "aiCsPrompt", key = "#record.groupid")
    public boolean add(AiCsPrompt record) {
        return aiCsPromptMapper.insert(record) > 0;
    }

    /** 删除条目并清缓存。 */
    @CacheEvict(value = "aiCsPrompt", key = "#groupId")
    public boolean delete(Integer id, String groupId) {
        return aiCsPromptMapper.deleteByIdAndGroupId(id, groupId) > 0;
    }

    /**
     * 修改条目名称并清缓存。
     * <p>
     * 必须 evict：ehcache 的 aiCsPrompt 是 eternal=true，不清缓存的话
     * 运行时拼接 system prompt 仍然会用到旧名称下的旧内容。
     */
    @CacheEvict(value = "aiCsPrompt", key = "#groupId")
    public boolean updateTitle(Integer id, String groupId, String title) {
        return aiCsPromptMapper.updateTitleByIdAndGroupId(id, groupId, title,
                String.valueOf(System.currentTimeMillis())) > 0;
    }

    /** 修改条目正文并清缓存（理由同 {@link #updateTitle}）。 */
    @CacheEvict(value = "aiCsPrompt", key = "#groupId")
    public boolean updatePrompt(Integer id, String groupId, String prompt) {
        return aiCsPromptMapper.updatePromptByIdAndGroupId(id, groupId, prompt,
                String.valueOf(System.currentTimeMillis())) > 0;
    }

    /** 清空某群条目并清缓存。 */
    @CacheEvict(value = "aiCsPrompt", key = "#groupId")
    public boolean clearByGroupId(String groupId) {
        aiCsPromptMapper.deleteByGroupId(groupId);
        return true;
    }
}
