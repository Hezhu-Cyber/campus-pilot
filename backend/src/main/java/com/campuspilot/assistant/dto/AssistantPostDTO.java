package com.campuspilot.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/** 助手场景下精简后的动态信息。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantPostDTO {

    private Long id;
    private Long activityId;
    private String title;
    private String content;
    private Integer liked;
    private Integer comments;
    private String name;
    private LocalDateTime createTime;
}
