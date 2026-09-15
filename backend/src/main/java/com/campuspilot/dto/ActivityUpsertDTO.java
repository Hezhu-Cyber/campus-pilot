package com.campuspilot.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 接收活动创建或更新时允许客户端填写的字段。 */
@Data
public class ActivityUpsertDTO {

    /** 主键 ID。 */
    private Long id;

    /** 名称。 */
    private String name;

    /** 活动分类 ID。 */
    private Long typeId;

    /** 详细描述。 */
    private String description;

    /** 逗号分隔的图片路径。 */
    private String images;

    /** 所属区域。 */
    private String area;

    /** 详细地址。 */
    private String address;

    /** 人均或参考价格。 */
    private Long avgPrice;

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
}
