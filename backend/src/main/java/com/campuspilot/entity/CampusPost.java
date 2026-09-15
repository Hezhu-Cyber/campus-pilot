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

/** 映射校园动态数据库表的领域实体。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_campus_post")
public class CampusPost implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联活动 ID。 */
    @TableField("activity_id")
    private Long activityId;

    /** 用户 ID。 */
    private Long userId;

    /** 图标或头像路径。 */
    @TableField(exist = false)
    private String icon;

    /** 名称。 */
    @TableField(exist = false)
    private String name;

    /** 当前用户是否已点赞，不持久化。 */
    @TableField(exist = false)
    private Boolean isLike;

    /** 标题。 */
    private String title;

    /** 逗号分隔的图片路径。 */
    private String images;

    /** 正文内容。 */
    private String content;

    /** 点赞数。 */
    private Integer liked;

    /** 评论数。 */
    private Integer comments;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

}
