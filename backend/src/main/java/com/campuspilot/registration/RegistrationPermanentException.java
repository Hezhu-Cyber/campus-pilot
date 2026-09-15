package com.campuspilot.registration;

public class RegistrationPermanentException extends RuntimeException {
    private final String code;

    public RegistrationPermanentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
