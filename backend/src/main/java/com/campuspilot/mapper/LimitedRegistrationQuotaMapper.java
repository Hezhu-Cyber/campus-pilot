package com.campuspilot.mapper;

import com.campuspilot.entity.LimitedRegistrationQuota;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 提供限量报名配额的 MyBatis-Plus 数据访问能力。 */
public interface LimitedRegistrationQuotaMapper extends BaseMapper<LimitedRegistrationQuota> {

    /** 数据库库存条件扣减，作为 Redis 预占后的最终一致性防线。 */
    @Update("UPDATE tb_limited_registration_quota SET stock = stock - 1 " +
            "WHERE registration_pass_id = #{registrationPassId} AND stock > 0")
    int decrementStock(@Param("registrationPassId") Long registrationPassId);

}
