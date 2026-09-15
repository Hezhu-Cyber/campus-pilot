package com.campuspilot.registration;

public final class RegistrationMqConstants {
    public static final String CREATE_TAG = "REGISTRATION_CREATE";
    public static final String DEAD_LETTER_TAG = "REGISTRATION_FAILED";
    public static final int MAX_RECONSUME_TIMES = 8;

    private RegistrationMqConstants() {
    }
}
