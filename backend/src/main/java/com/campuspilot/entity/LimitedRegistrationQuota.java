package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 映射限量报名配额数据库表的领域实体。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_limited_registration_quota")
public class LimitedRegistrationQuota implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联报名凭证 ID。 */
    @TableId(value = "registration_pass_id", type = IdType.INPUT)
    private Long registrationPassId;

    /** 可用名额库存。 */
    private Integer stock;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 生效或开抢时间。 */
    private LocalDateTime beginTime;

    /** 活动结束时间。 */
    private LocalDateTime endTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

}
