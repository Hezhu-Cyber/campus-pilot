package com.campuspilot.dto;

import lombok.Data;

/** 向前端和登录上下文暴露的安全用户摘要。 */
@Data
public class UserDTO {

    /** 主键 ID。 */
    private Long id;

    /** 用户昵称。 */
    private String nickName;

    /** 图标或头像路径。 */
    private String icon;

    /** 用户角色。 */
    private String role;
}
