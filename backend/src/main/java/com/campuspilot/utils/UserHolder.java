package com.campuspilot.utils;

import com.campuspilot.dto.UserDTO;

/** 使用 ThreadLocal 在一次请求内保存当前用户。 */
public class UserHolder {

    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    /** 将当前登录用户绑定到请求线程。 */
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    /** 获取当前请求线程中的登录用户。 */
    public static UserDTO getUser(){
        return tl.get();
    }

    /** 清理当前请求线程中的登录用户。 */
    public static void removeUser(){
        tl.remove();
    }
}
