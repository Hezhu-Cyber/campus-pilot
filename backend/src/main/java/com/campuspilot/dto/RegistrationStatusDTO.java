package com.campuspilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationStatusDTO {
    private Long registrationId;
    private String status;
    private String failureCode;
    private String failureReason;
}
