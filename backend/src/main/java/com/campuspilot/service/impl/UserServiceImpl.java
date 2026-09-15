package com.campuspilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campuspilot.dto.LoginFormDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.entity.User;
import com.campuspilot.mapper.UserMapper;
import com.campuspilot.service.IUserService;
import com.campuspilot.utils.PasswordEncoder;
import com.campuspilot.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.campuspilot.utils.RedisConstants.*;

/** 实现用户的业务规则与持久化协调。 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.auth.expose-demo-code:false}")
    private boolean exposeDemoCode;

    /** 校验手机号与发送频率后，生成并缓存验证码。 */
    @Override
    public Result sendCode(String phone) {

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        // setIfAbsent 同时完成“首次发送”判断和 60 秒限流窗口设置。
        Boolean firstRequest = stringRedisTemplate.opsForValue().setIfAbsent(
                LOGIN_CODE_RATE_KEY + phone, "1", 60, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstRequest)) {
            return Result.fail("验证码发送过于频繁，请稍后再试");
        }

        String code = RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.delete(LOGIN_CODE_ATTEMPT_KEY + phone);

        log.info("已为手机号 {}****{} 生成登录验证码", phone.substring(0, 3), phone.substring(7));

        return exposeDemoCode ? Result.ok(code) : Result.ok();
    }

    /** 根据密码或验证码验证身份，然后将登录令牌写入 Redis。 */
    @Override
    public Result login(LoginFormDTO loginForm) {

        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        User user;
        if (StrUtil.isNotBlank(loginForm.getPassword())) {
            // 密码登录与验证码登录共用同一入参，密码非空时优先走此分支。
            if (loginForm.getPassword().length() > 128) return Result.fail("手机号或密码错误");
            if (attemptsExceeded(LOGIN_PASSWORD_ATTEMPT_KEY + phone)) {
                return Result.fail("登录失败次数过多，请15分钟后再试");
            }
            user = query().eq("phone", phone).one();
            if (user == null || StrUtil.isBlank(user.getPassword())
                    || !PasswordEncoder.matches(user.getPassword(), loginForm.getPassword())) {
                recordFailedAttempt(LOGIN_PASSWORD_ATTEMPT_KEY + phone, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
                return Result.fail("手机号或密码错误");
            }
            stringRedisTemplate.delete(LOGIN_PASSWORD_ATTEMPT_KEY + phone);
            if (!user.getPassword().startsWith("pbkdf2$")) {
                // 旧哈希在一次成功登录后自动升级为 PBKDF2。
                user.setPassword(PasswordEncoder.encode(loginForm.getPassword()));
                updateById(user);
            }
        } else {
            // 验证码登录使用独立失败计数，不影响密码登录的锁定窗口。
            if (attemptsExceeded(LOGIN_CODE_ATTEMPT_KEY + phone)) {
                return Result.fail("验证码错误次数过多，请重新获取验证码");
            }
            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            String code = loginForm.getCode();
            if (code == null || code.length() != 6 || cacheCode == null || !cacheCode.equals(code)) {
                recordFailedAttempt(LOGIN_CODE_ATTEMPT_KEY + phone, LOGIN_CODE_TTL, TimeUnit.MINUTES);
                return Result.fail("验证码错误");
            }
            user = query().eq("phone", phone).one();
            if (user == null) {
                user = createUserWithPhone(phone);
            }
        }

        String token=UUID.randomUUID().toString(true);

        // Redis Hash 只保存 UserDTO 白名单字段，避免密码进入登录会话。
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
        CopyOptions
                .create().setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName,fieldValue) ->fieldValue.toString()));

        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        if (StrUtil.isNotBlank(loginForm.getCode())) {
            stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
            stringRedisTemplate.delete(LOGIN_CODE_ATTEMPT_KEY + phone);
        }

        return Result.ok(token);
    }

    /** 为首次验证码登录的手机号创建默认学生账号。 */
    private User createUserWithPhone(String phone) {

        User user = new User();
        user.setPhone(phone);
        user.setNickName("user"+RandomUtil.randomString(10));
        user.setRole("STUDENT");

        save(user);
        return user;

    }

    /** 判断 Redis 中记录的失败次数是否达到限制。 */
    private boolean attemptsExceeded(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(value)) return false;
        try {
            return Long.parseLong(value) >= LOGIN_MAX_ATTEMPTS;
        } catch (NumberFormatException e) {
            stringRedisTemplate.delete(key);
            return false;
        }
    }

    /** 累加失败次数，并在首次失败时设置锁定窗口。 */
    private void recordFailedAttempt(String key, long ttl, TimeUnit unit) {
        Long attempts = stringRedisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) stringRedisTemplate.expire(key, ttl, unit);
    }
}
