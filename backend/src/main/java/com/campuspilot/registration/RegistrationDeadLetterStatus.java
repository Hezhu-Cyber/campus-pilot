package com.campuspilot.registration;

/** 报名死信的生命周期状态。 */
public enum RegistrationDeadLetterStatus {
    PENDING,
    AUTO_RETRYING,
    AUTO_REPLAYED,
    MANUAL_REQUIRED,
    REPLAYED
}
