package com.campuspilot.dto;

import lombok.Data;

/** 接收发布校园动态时允许客户端填写的字段。 */
@Data
public class CreatePostDTO {

    /** 关联活动 ID。 */
    private Long activityId;

    /** 标题。 */
    private String title;

    /** 逗号分隔的图片路径。 */
    private String images;

    /** 正文内容。 */
    private String content;
}
