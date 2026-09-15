package com.campuspilot.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/** 助手场景下精简后的活动信息。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantActivityDTO {

    private Long id;
    private String name;
    private String description;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Long avgPrice;
    private Integer sold;
    private String openHours;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private Integer capacity;
}
