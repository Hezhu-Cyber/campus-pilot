package com.campuspilot.registration;

public enum RegistrationStatus {
    PENDING,
    RESERVED,
    PROCESSING,
    SUCCESS,
    FAILED,
    COMPENSATED//意思是**"已补偿 / 已了结"——它表示这条报名流水没能正常走完，但已经做了"善后清理"，事情最终了结**。它是报名状态机里的一个终态。
}
