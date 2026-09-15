package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 映射活动分类数据库表的领域实体。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_activity_category")
public class ActivityCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 名称。 */
    private String name;

    /** 图标或头像路径。 */
    private String icon;

    /** 分类展示顺序。 */
    private Integer sort;

    /** 创建时间。 */
    @JsonIgnore
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    @JsonIgnore
    private LocalDateTime updateTime;

}
