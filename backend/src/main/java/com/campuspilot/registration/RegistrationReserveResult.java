package com.campuspilot.registration;

public enum RegistrationReserveResult {
    SUCCESS(0, null),
    OUT_OF_STOCK(1, "活动名额已满"),
    DUPLICATE(2, "不能重复报名"),
    NOT_STARTED(3, "报名尚未开始"),
    ENDED(4, "报名已经结束"),
    UNAVAILABLE(5, "报名活动不存在或未启用");

    private final int code;
    private final String message;

    RegistrationReserveResult(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public static RegistrationReserveResult fromCode(long code) {
        for (RegistrationReserveResult value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNAVAILABLE;
    }
}
