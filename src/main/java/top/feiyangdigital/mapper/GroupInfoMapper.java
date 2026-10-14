package top.feiyangdigital.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import top.feiyangdigital.entity.GroupInfo;
import top.feiyangdigital.entity.GroupInfoExample;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;

@Repository
public interface GroupInfoMapper {
    long countByExample(GroupInfoExample example);

    int deleteByExample(GroupInfoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(GroupInfoWithBLOBs record);

    int insertSelective(GroupInfoWithBLOBs record);

    List<GroupInfoWithBLOBs> selectByExampleWithBLOBs(GroupInfoExample example);

    List<GroupInfo> selectByExample(GroupInfoExample example);

    GroupInfoWithBLOBs selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") GroupInfoWithBLOBs record, @Param("example") GroupInfoExample example);

    int updateByExampleWithBLOBs(@Param("record") GroupInfoWithBLOBs record, @Param("example") GroupInfoExample example);

    int updateByExample(@Param("record") GroupInfo record, @Param("example") GroupInfoExample example);

    int updateByPrimaryKeySelective(GroupInfoWithBLOBs record);

    int updateByPrimaryKeyWithBLOBs(GroupInfoWithBLOBs record);

    int updateByPrimaryKey(GroupInfo record);

    /**
     * 读取「AI进群昵称/简介检测」开关（intoGroupAiCheckFlag）。
     * 该字段未包含在生成的实体/XML 中，故此处用注解方式单独读写，避免改动大量生成代码。
     */
    @Select("select intoGroupAiCheckFlag from groupinfo where groupId = #{groupId}")
    String selectIntoGroupAiCheckFlag(@Param("groupId") String groupId);

    /** 设置「AI进群昵称/简介检测」开关，返回受影响行数。 */
    @Update("update groupinfo set intoGroupAiCheckFlag = #{flag} where groupId = #{groupId}")
    int updateIntoGroupAiCheckFlag(@Param("groupId") String groupId, @Param("flag") String flag);
}