package com.campuspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 智能客服转人工后生成的可追踪工单。 */
@Data
@Accessors(chain = true)
@TableName("tb_support_ticket")
public class SupportTicket implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String threadId;
    private String category;
    private String subject;
    private String content;
    private Long handlerId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
