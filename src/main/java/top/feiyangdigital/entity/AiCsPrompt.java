package top.feiyangdigital.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * ai_cs_prompt —— AI 客服知识条目。
 * <p>
 * 每条记录代表一段「知识/规则」，一个群可以有多条；
 * 运行时会把该群所有条目按 sortOrder 拼接成一段 system prompt。
 * <p>
 * 该表为本项目自定义，非 MyBatis Generator 生成，字段命名沿用项目风格（全小写）。
 *
 * @author Cnievana
 */
@Data
public class AiCsPrompt implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，同时用作面板回调数据（删除时使用） */
    private Integer id;

    /** 所属群组 ID */
    private String groupid;

    /** 面板显示名称（备注），用于在列表中区分各条目 */
    private String title;

    /** 提示词正文 */
    private String prompt;

    /** 排序号，越小越靠前 */
    private Integer sortorder;

    /** 最后更新时间戳（毫秒） */
    private String updatetimestamp;
}
