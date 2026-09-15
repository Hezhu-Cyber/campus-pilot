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

//活动方/管理员配置的活动报名方案
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_registration_pass")
public class RegistrationPass implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联活动 ID。 */
    @TableField("activity_id")
    private Long activityId;

    /** 标题。 */
    private String title;

    /** 副标题。 */
    private String subTitle;

    /** 凭证使用规则。 */
    private String rules;

    /** 需支付金额。 */
    private Long payValue;

    /** 凭证实际价值。 */
    private Long actualValue;

    /** 凭证类型，用于区分普通与限量报名。 */
    private Integer type;

    /** 当前业务状态。 */
    private Integer status;

    /** 可用名额库存。 */
    @TableField(exist = false)
    private Integer stock;

    /** 生效或开抢时间。 */
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /** 活动结束时间。 */
    @TableField(exist = false)
    private LocalDateTime endTime;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

}
