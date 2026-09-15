package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 映射活动数据库表的领域实体。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_activity")
public class Activity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 活动组织者 ID。 */
    private Long organizerId;

    /**
     * 名称。
     */
    private String name;

    /** 详细描述。 */
    private String description;

    /** 活动分类 ID。 */
    private Long typeId;

    /** 逗号分隔的图片路径。 */
    private String images;

    /** 所属区域。 */
    private String area;

    /** 详细地址。 */
    private String address;

    /** 人均或参考价格。 */
    private Long avgPrice;

    /** 已报名或已售数量。 */
    private Integer sold;

    /** 活动或场地开放时间说明。 */
    private String openHours;

    /** 活动开始时间。 */
    private LocalDateTime startTime;

    /** 活动结束时间。 */
    private LocalDateTime endTime;

    /** 报名截止时间。 */
    private LocalDateTime registrationDeadline;

    /** 活动容量。 */
    private Integer capacity;

    /** 活动发布状态。 */
    private String activityStatus;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

}
