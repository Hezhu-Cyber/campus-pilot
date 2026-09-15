package com.campuspilot.utils;

import cn.hutool.core.util.StrUtil;

/** 提供对空值安全的正则格式校验。 */
public class RegexUtils {

    /** 判断手机号是否为空或不符合规则。 */
    public static boolean isPhoneInvalid(String phone){
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }

    /** 对空值安全地判断字符串是否不匹配正则。 */
    private static boolean mismatch(String str, String regex){
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
