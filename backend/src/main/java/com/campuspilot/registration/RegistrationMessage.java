package com.campuspilot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private String eventId;
    private Long registrationId;
    private Long userId;
    private Long registrationPassId;
    private Long requestTime;
    private Integer schemaVersion;
}
