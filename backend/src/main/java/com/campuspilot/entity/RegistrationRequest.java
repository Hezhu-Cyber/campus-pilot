package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_registration_request")
public class RegistrationRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "registration_id", type = IdType.INPUT)
    private Long registrationId;
    private Long userId;
    private Long registrationPassId;
    private String eventId;
    private String messageId;
    private String status;
    private Integer retryCount;
    private String failureCode;
    private String failureReason;
    @Version
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
