package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 映射动态评论数据库表的领域实体。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_campus_post_comment")
public class CampusPostComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 关联动态 ID。 */
    @TableField("post_id")
    private Long postId;

    /** 父评论 ID，顶级评论为空。 */
    private Long parentId;

    /** 被回复的用户或评论关联 ID。 */
    private Long answerId;

    /** 正文内容。 */
    private String content;

    /** 点赞数。 */
    private Integer liked;

    /** 当前业务状态。 */
    private Boolean status;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

    /** 仅用于响应展示的评论者昵称。 */
    @TableField(exist = false)
    private String userName;

    /** 仅用于响应展示的评论者头像。 */
    @TableField(exist = false)
    private String userIcon;

}
