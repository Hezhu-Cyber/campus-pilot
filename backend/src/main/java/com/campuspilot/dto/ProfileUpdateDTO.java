package com.campuspilot.dto;

import lombok.Data;
import java.time.LocalDate;

/** 接收当前用户可修改的个人资料。 */
@Data
public class ProfileUpdateDTO {

    /** 用户昵称。 */
    private String nickName;

    /** 图标或头像路径。 */
    private String icon;

    /** 所在城市。 */
    private String city;

    /** 个人介绍。 */
    private String introduce;

    /** 用户性别。 */
    private Boolean gender;

    /** 出生日期。 */
    private LocalDate birthday;
}
