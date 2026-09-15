package com.campuspilot.dto;

import lombok.Data;

/** 接收密码登录或验证码登录参数。 */
@Data
public class LoginFormDTO {

    /** 登录手机号。 */
    private String phone;

    /** 手机验证码。 */
    private String code;

    /** 加盐哈希后的密码。 */
    private String password;
}
