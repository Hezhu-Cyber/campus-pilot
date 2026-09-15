package com.campuspilot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campuspilot.dto.LoginFormDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.entity.User;

/** 用户认证服务。 */
public interface IUserService extends IService<User> {

    /** 生成并缓存手机验证码。 */
    Result sendCode(String phone);

    /** 使用密码或验证码登录并签发令牌。 */
    Result login(LoginFormDTO loginForm);
}
