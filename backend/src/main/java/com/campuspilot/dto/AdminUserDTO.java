package com.campuspilot.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理员工作台使用的用户摘要，不包含密码等敏感字段。 */
@Data
public class AdminUserDTO {

    private Long id;

    private String phone;

    private String nickName;

    private String icon;

    private String role;

    private LocalDateTime createTime;
}
