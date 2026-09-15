package com.campuspilot.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/** 助手可见的报名凭证摘要。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantRegistrationPassDTO {

    private Long id;
    private Long activityId;
    private String activityName;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Integer type;
    private Integer status;
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private String address;
    private LocalDateTime startTime;
}
