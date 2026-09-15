package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_registration_dead_letter")
public class RegistrationDeadLetter implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long registrationId;
    private String messageId;
    private Integer reconsumeTimes;
    private String failureCode;
    private String failureReason;
    private String payload;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
