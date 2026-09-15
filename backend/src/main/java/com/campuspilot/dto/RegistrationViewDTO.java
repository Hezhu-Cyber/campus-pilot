package com.campuspilot.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 组合报名记录、凭证和活动信息供前端展示。 */
@Data
public class RegistrationViewDTO {

    /** 主键 ID。 */
    private Long id;

    /** 关联报名凭证 ID。 */
    private Long registrationPassId;

    /** 当前业务状态。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 关联活动 ID。 */
    private Long activityId;

    /** 标题。 */
    private String title;

    /** activityName 字段。 */
    private String activityName;

    /** 详细地址。 */
    private String address;

    /** 活动或场地开放时间说明。 */
    private String openHours;
}
