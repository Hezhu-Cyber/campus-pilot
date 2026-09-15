package com.campuspilot.service.impl;

import com.campuspilot.entity.LimitedRegistrationQuota;
import com.campuspilot.mapper.LimitedRegistrationQuotaMapper;
import com.campuspilot.service.ILimitedRegistrationQuotaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/** 实现限量报名配额的业务规则与持久化协调。 */
@Service
public class LimitedRegistrationQuotaServiceImpl extends ServiceImpl<LimitedRegistrationQuotaMapper, LimitedRegistrationQuota> implements ILimitedRegistrationQuotaService {

}
