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

//用户报名的记录
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_activity_registration")
public class ActivityRegistration implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 关联报名凭证 ID。 */
    @TableField("registration_pass_id")
    private Long registrationPassId;

    /** 支付方式。 */
    private Integer payType;

    /** 当前业务状态。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 支付时间。 */
    private LocalDateTime payTime;

    /** 核销时间。 */
    private LocalDateTime useTime;

    /** 退款或取消时间。 */
    private LocalDateTime refundTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

}
