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

    /**
     * 读取「AI违规自动禁言」开关（aiMuteFlag）。
     * 该字段同样未包含在生成的实体/XML 中，用注解单独读写。
     */
    @Select("select aiMuteFlag from groupinfo where groupId = #{groupId}")
    String selectAiMuteFlag(@Param("groupId") String groupId);

    /** 设置「AI违规自动禁言」开关，返回受影响行数。 */
    @Update("update groupinfo set aiMuteFlag = #{flag} where groupId = #{groupId}")
    int updateAiMuteFlag(@Param("groupId") String groupId, @Param("flag") String flag);

    // ==================== AI 客服（aiCs）相关字段 ====================
    // 同 intoGroupAiCheckFlag，这几个字段未包含在生成的实体/XML 中，故用注解单独读写。

    /** 读取「AI客服」总开关。 */
    @Select("select aiCsFlag from groupinfo where groupId = #{groupId}")
    String selectAiCsFlag(@Param("groupId") String groupId);

    /** 设置「AI客服」总开关。 */
    @Update("update groupinfo set aiCsFlag = #{flag} where groupId = #{groupId}")
    int updateAiCsFlag(@Param("groupId") String groupId, @Param("flag") String flag);

    /** 读取 AI客服群内触发方式（at / keyword / all）。 */
    @Select("select aiCsTriggerMode from groupinfo where groupId = #{groupId}")
    String selectAiCsTriggerMode(@Param("groupId") String groupId);

    /** 设置 AI客服群内触发方式。 */
    @Update("update groupinfo set aiCsTriggerMode = #{mode} where groupId = #{groupId}")
    int updateAiCsTriggerMode(@Param("groupId") String groupId, @Param("mode") String mode);

    /** 读取 AI客服关键词（keyword 触发方式下使用的正则）。 */
    @Select("select aiCsKeyword from groupinfo where groupId = #{groupId}")
    String selectAiCsKeyword(@Param("groupId") String groupId);

    /** 设置 AI客服关键词。 */
    @Update("update groupinfo set aiCsKeyword = #{keyword} where groupId = #{groupId}")
    int updateAiCsKeyword(@Param("groupId") String groupId, @Param("keyword") String keyword);

    /** 读取「私聊AI客服」开关。 */
    @Select("select aiCsPrivateFlag from groupinfo where groupId = #{groupId}")
    String selectAiCsPrivateFlag(@Param("groupId") String groupId);
    /** 设置「私聊AI客服」开关。 */
    @Update("update groupinfo set aiCsPrivateFlag = #{flag} where groupId = #{groupId}")
    int updateAiCsPrivateFlag(@Param("groupId") String groupId, @Param("flag") String flag);

    /**
     * 反查该管理员名下「已开启总开关 + 已开启私聊客服」的群，用于容器重启后恢复私聊绑定。
     * <p>
     * ownerAndAnonymousAdmins 里存的是「userId|userId|...」（见 CheckUser.fetchHighAdminList）。
     */
    @Select("select groupId from groupinfo where aiCsFlag = 'open' and aiCsPrivateFlag = 'open' " +
            "and ownerAndAnonymousAdmins like concat('%', #{userId}, '%') order by id asc limit 1")
    String selectAiCsPrivateGroupId(@Param("userId") String userId);

    /**
     * 查询该管理员（群主 / 匿名管理员）名下的所有群组，用于私聊 /start 的群组列表。
     * <p>
     * ownerAndAnonymousAdmins 存的是「userId|userId|...」（见 CheckUser.fetchHighAdminList），
     * 故先给两端补上「|」再做匹配，避免 userId=123 误命中 1234。
     * 只取 groupId / groupName 两列，不涉及 BLOB，开销很小。
     */
    @Select("select groupId, groupName from groupinfo " +
            "where concat('|', ownerAndAnonymousAdmins, '|') like concat('%|', #{userId}, '|%') " +
            "order by id asc")
    List<GroupInfo> selectGroupsManagedBy(@Param("userId") String userId);
}