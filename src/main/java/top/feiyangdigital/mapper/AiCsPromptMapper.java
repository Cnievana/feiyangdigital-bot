package top.feiyangdigital.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;
import top.feiyangdigital.entity.AiCsPrompt;

import java.util.List;

/**
 * AI 客服知识条目（ai_cs_prompt）数据访问。
 * <p>
 * 采用注解方式手写，避免引入 MyBatis Generator 的 Example 类。
 * 因为 application.yml 未开启 mapUnderscoreToCamelCase，这里统一用 SQL 别名做映射。
 *
 * @author Cnievana
 */
@Repository
public interface AiCsPromptMapper {

    String COLUMNS = "id, groupId as groupid, title, prompt, sortOrder as sortorder, updateTimestamp as updatetimestamp";

    /** 查询某群的全部知识条目，按排序号升序。 */
    @Select("select " + COLUMNS + " from ai_cs_prompt where groupId = #{groupId} order by sortOrder asc, id asc")
    List<AiCsPrompt> selectByGroupId(@Param("groupId") String groupId);

    /** 按主键查询（会校验所属群，避免越权删除）。 */
    @Select("select " + COLUMNS + " from ai_cs_prompt where id = #{id} and groupId = #{groupId}")
    AiCsPrompt selectByIdAndGroupId(@Param("id") Integer id, @Param("groupId") String groupId);

    /** 统计某群条目数量。 */
    @Select("select count(*) from ai_cs_prompt where groupId = #{groupId}")
    int countByGroupId(@Param("groupId") String groupId);

    /** 新增条目，回填自增主键。 */
    @Insert("insert into ai_cs_prompt (groupId, title, prompt, sortOrder, updateTimestamp) " +
            "values (#{groupid}, #{title}, #{prompt}, #{sortorder}, #{updatetimestamp})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiCsPrompt record);

    /** 删除某群的指定条目。 */
    @Delete("delete from ai_cs_prompt where id = #{id} and groupId = #{groupId}")
    int deleteByIdAndGroupId(@Param("id") Integer id, @Param("groupId") String groupId);

    /** 清空某群全部条目。 */
    @Delete("delete from ai_cs_prompt where groupId = #{groupId}")
    int deleteByGroupId(@Param("groupId") String groupId);
}
