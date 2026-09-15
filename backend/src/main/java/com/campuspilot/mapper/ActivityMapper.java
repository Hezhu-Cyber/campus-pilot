package com.campuspilot.mapper;

import com.campuspilot.entity.Activity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Update;

/** 提供活动的 MyBatis-Plus 数据访问能力。 */
public interface ActivityMapper extends BaseMapper<Activity> {

    /**
     * 按报名记录统计结果重建活动表已报名数。
     * 只统计 status <> 4（未取消）的报名记录，普通与限量报名共用。
     */
    @Update("UPDATE tb_activity a " +
            "LEFT JOIN (" +
            "  SELECT p.activity_id AS activity_id, COUNT(*) AS cnt " +
            "  FROM tb_activity_registration r " +
            "  INNER JOIN tb_registration_pass p ON r.registration_pass_id = p.id " +
            "  WHERE r.status <> 4 " +
            "  GROUP BY p.activity_id" +
            ") s ON s.activity_id = a.id " +
            "SET a.sold = IFNULL(s.cnt, 0)")
    int recomputeSold();
}
