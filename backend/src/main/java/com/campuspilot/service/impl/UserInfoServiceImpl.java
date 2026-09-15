package com.campuspilot.service.impl;

import com.campuspilot.entity.UserInfo;
import com.campuspilot.mapper.UserInfoMapper;
import com.campuspilot.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/** 实现用户资料的业务规则与持久化协调。 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
