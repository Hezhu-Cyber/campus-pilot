package com.campuspilot.service.impl;

import com.campuspilot.entity.ActivityCategory;
import com.campuspilot.mapper.ActivityCategoryMapper;
import com.campuspilot.service.IActivityCategoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/** 实现活动分类的业务规则与持久化协调。 */
@Service
public class ActivityCategoryServiceImpl extends ServiceImpl<ActivityCategoryMapper, ActivityCategory> implements IActivityCategoryService {

}
