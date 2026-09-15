package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 映射用户资料数据库表的领域实体。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 所在城市。 */
    private String city;

    /** 个人介绍。 */
    private String introduce;

    /** 用户性别。 */
    private Boolean gender;

    /** 出生日期。 */
    private LocalDate birthday;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

}
