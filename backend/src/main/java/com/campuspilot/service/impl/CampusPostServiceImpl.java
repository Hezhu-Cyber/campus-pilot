package com.campuspilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.entity.CampusPost;
import com.campuspilot.entity.User;
import com.campuspilot.mapper.CampusPostMapper;
import com.campuspilot.service.ICampusPostService;
import com.campuspilot.service.IUserService;
import com.campuspilot.service.IActivityService;
import com.campuspilot.utils.SystemConstants;
import com.campuspilot.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.campuspilot.utils.RedisConstants.POST_LIKED_KEY;

/** 实现校园动态的业务规则与持久化协调。 */
@Service
public class CampusPostServiceImpl extends ServiceImpl<CampusPostMapper, CampusPost> implements ICampusPostService {
    private static final DefaultRedisScript<Long> TOGGLE_LIKE_SCRIPT;
    static {
        // 用 Lua 保证“判断是否点赞+加入或移除 ZSet”不被并发请求打断。
        TOGGLE_LIKE_SCRIPT = new DefaultRedisScript<>();
        TOGGLE_LIKE_SCRIPT.setLocation(new ClassPathResource("toggle_like.lua"));
        TOGGLE_LIKE_SCRIPT.setResultType(Long.class);
    }
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IActivityService activityService;

    /** 查询动态详情并补充作者和当前用户点赞状态。 */
    @Override
    public Result queryPostById(Long id) {

        CampusPost post = getById(id);
        if(post==null)
        {
            return Result.fail("笔记不存在！");
        }

        populatePostAuthor(post);

        populateLikeStatus(post);
        return Result.ok(post);
    }

    /** 从 Redis 补充当前用户是否点赞。 */
    private void populateLikeStatus(CampusPost post) {

        UserDTO user= UserHolder.getUser();
        if(user==null)
        {

            return;
        }
        Long userId = UserHolder.getUser().getId();

        String key = "post:liked:" + post.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        post.setIsLike(score!=null);
    }

    /** 按点赞数倒序分页查询热门动态。 */
    @Override
    public Result queryHotPosts(Integer current) {

        Page<CampusPost> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<CampusPost> records = page.getRecords();

        records.forEach(post -> {
            this.populatePostAuthor(post);
            this.populateLikeStatus(post);
        });
        return Result.ok(records);
    }

    /** 分页返回指定活动下的动态。 */
    @Override
    public Result queryPostsByActivity(Long activityId, Integer current) {
        Page<CampusPost> page = query().eq("activity_id", activityId).orderByDesc("create_time")
                .page(new Page<>(Math.max(current, 1), SystemConstants.MAX_PAGE_SIZE));
        page.getRecords().forEach(post -> {
            populatePostAuthor(post);
            populateLikeStatus(post);
        });
        return Result.ok(page.getRecords(), page.getTotal());
    }

    /** 通过 Lua 原子切换点赞状态，并同步数据库计数。 */
    @Override
    public Result togglePostLike(Long id) {
        if (getById(id) == null) return Result.fail("动态不存在");
        Long userId = UserHolder.getUser().getId();
        String key = "post:liked:" + id;
        Long delta = stringRedisTemplate.execute(TOGGLE_LIKE_SCRIPT,
                Collections.singletonList(key), userId.toString(), String.valueOf(System.currentTimeMillis()));
        if (delta == null) return Result.fail("点赞服务暂时不可用");
        boolean success = update().setSql(delta > 0 ? "liked = IFNULL(liked, 0) + 1"
                : "liked = GREATEST(IFNULL(liked, 0) - 1, 0)").eq("id", id).update();
        if (!success) {
            // 数据库计数更新失败时再执行一次切换，将 Redis 恢复到请求前状态。
            stringRedisTemplate.execute(TOGGLE_LIKE_SCRIPT,
                    Collections.singletonList(key), userId.toString(), String.valueOf(System.currentTimeMillis()));
            return Result.fail("点赞失败，请重试");
        }
        return Result.ok(delta > 0);
    }

    /** 按点赞时间返回前五位点赞用户。 */
    @Override
    public Result queryPostLikes(Long id) {
        String key = POST_LIKED_KEY + id;

        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty())
        {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idstr= StrUtil.join(",",ids);

        // FIELD 保留 ZSet 中的点赞时间顺序，避免 IN 查询按主键重排。
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id,"+ idstr +")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    /** 校验内容和图片归属后发布动态。 */
    @Override
    public Result publishPost(CampusPost post) {
        if (post == null || StrUtil.isBlank(post.getTitle()) || post.getTitle().length() > 255) {
            return Result.fail("动态标题应为1到255个字符");
        }
        if (StrUtil.isBlank(post.getContent()) || post.getContent().length() > 2000) {
            return Result.fail("动态内容应为1到2000个字符");
        }
        if (post.getActivityId() == null) return Result.fail("请选择关联活动");
        if (activityService.getById(post.getActivityId()) == null) return Result.fail("关联活动不存在");
        if (StrUtil.isBlank(post.getImages()) || post.getImages().split(",").length > 9) {
            return Result.fail("请上传1到9张图片");
        }

        UserDTO user = UserHolder.getUser();
        post.setUserId(user.getId());
        post.setId(null);
        post.setLiked(0);
        post.setComments(0);
        post.setCreateTime(null);
        post.setUpdateTime(null);
        for (String image : post.getImages().split(",")) {
            if (!image.trim().startsWith("/posts/" + user.getId() + "/")) {
                return Result.fail("动态只能使用当前用户上传的图片");
            }
        }

        boolean isSuccess = save(post);
        if(!isSuccess)
        {
            return Result.fail("新增笔记失败");
        }

        return Result.ok(post.getId());
    }

    /** 从用户表补充动态作者昵称和头像。 */
    private void populatePostAuthor(CampusPost post) {
        Long userId = post.getUserId();
        User user = userService.getById(userId);
        if (user != null) {
            post.setName(user.getNickName());
            post.setIcon(user.getIcon());
        } else {
            post.setName("已注销用户");
            post.setIcon("");
        }
    }
}
