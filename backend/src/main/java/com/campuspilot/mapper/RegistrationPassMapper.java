package com.campuspilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspilot.entity.RegistrationPass;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 提供报名凭证的 MyBatis-Plus 数据访问能力。 */
public interface RegistrationPassMapper extends BaseMapper<RegistrationPass> {

    /** 查询活动下的报名凭证。 */
    List<RegistrationPass> queryPassesByActivity(@Param("activityId") Long activityId);
}
