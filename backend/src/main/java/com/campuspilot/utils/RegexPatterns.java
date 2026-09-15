package com.campuspilot.utils;

/** 集中定义请求参数的正则表达式。 */
public abstract class RegexPatterns {

    /** 中国大陆手机号格式。 */
    public static final String PHONE_REGEX = "^1([38][0-9]|4[579]|5[0-3,5-9]|6[6]|7[0135678]|9[89])\\d{8}$";
}
