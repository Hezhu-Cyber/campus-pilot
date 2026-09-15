package com.campuspilot.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuspilot.dto.AdminUserDTO;
import com.campuspilot.dto.LoginFormDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.dto.ProfileUpdateDTO;
import com.campuspilot.entity.UserInfo;
import com.campuspilot.entity.User;
import com.campuspilot.service.IUserInfoService;
import com.campuspilot.service.IUserService;
import com.campuspilot.utils.UserHolder;
import com.campuspilot.utils.RoleGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.campuspilot.utils.RedisConstants.LOGIN_USER_KEY;

/** 提供用户相关的 HTTP 接口。 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RoleGuard roleGuard;

    /** 校验手机号与发送频率后，生成并缓存验证码。 */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /** 根据密码或验证码验证身份，然后将登录令牌写入 Redis。 */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /** 删除 Redis 中的当前登录令牌。 */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        return Result.ok();
    }

    /** 返回当前登录用户的安全摘要。 */
    @GetMapping("/me")
    public Result me() {
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /** 管理员分页查看用户和角色，响应中不包含密码。 */
    @GetMapping("/admin/users")
    public Result adminUsers(@RequestParam(value = "current", defaultValue = "1") Integer current,
                             @RequestParam(value = "keyword", required = false) String keyword) {
        roleGuard.requireAdmin();
        if (current == null || current < 1) return Result.fail("页码参数错误");

        Page<User> page = userService.query()
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like("phone", keyword)
                        .or()
                        .like("nick_name", keyword))
                .orderByAsc("id")
                .page(new Page<>(current, 20));
        List<AdminUserDTO> users = page.getRecords().stream()
                .map(user -> BeanUtil.copyProperties(user, AdminUserDTO.class))
                .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("records", users);
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("pages", page.getPages());
        return Result.ok(result);
    }

    /** 根据 ID 查询用户并过滤密码等敏感字段。 */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    /** 查询指定用户的扩展资料。 */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {

        UserInfo info = userInfoService.getById(userId);
        if (info == null) {

            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);

        return Result.ok(info);
    }

    /** 校验并更新当前用户的基本信息、扩展资料与令牌快照。 */
    @PutMapping("/profile")
    @Transactional
    public Result updateProfile(@RequestBody ProfileUpdateDTO profile,
                                @RequestHeader(value = "authorization", required = false) String token) {
        if (StrUtil.isBlank(profile.getNickName()) || profile.getNickName().trim().length() > 32) {
            return Result.fail("昵称应为1到32个字符");
        }
        if (profile.getIntroduce() != null && profile.getIntroduce().length() > 128) {
            return Result.fail("个人介绍不能超过128个字符");
        }
        Long userId = UserHolder.getUser().getId();
        User user = new User();
        user.setId(userId);
        user.setNickName(profile.getNickName().trim());
        user.setIcon(StrUtil.nullToEmpty(profile.getIcon()));
        userService.updateById(user);

        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            info = new UserInfo();
            info.setUserId(userId);
        }
        info.setCity(profile.getCity());
        info.setIntroduce(profile.getIntroduce());
        info.setGender(profile.getGender());
        info.setBirthday(profile.getBirthday());
        userInfoService.saveOrUpdate(info);

        UserDTO current = UserHolder.getUser();
        current.setNickName(user.getNickName());
        current.setIcon(user.getIcon());
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, "nickName", current.getNickName());
            stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, "icon", current.getIcon());
        }
        return Result.ok();
    }

}
