package com.campuspilot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationDeadLetterMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private RegistrationMessage originalMessage;
    private String rocketMqMessageId;
    private Integer reconsumeTimes;
    private String failureCode;
    private String failureReason;
    private Long failedAt;
}
