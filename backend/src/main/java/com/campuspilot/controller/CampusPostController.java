package com.campuspilot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.dto.CreatePostDTO;
import com.campuspilot.entity.CampusPost;
import com.campuspilot.service.ICampusPostService;
import com.campuspilot.utils.SystemConstants;
import com.campuspilot.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/** 提供校园动态相关的 HTTP 接口。 */
@RestController
@RequestMapping("/post")
public class CampusPostController {

    @Resource
    private ICampusPostService campusPostService;

    /** 校验内容和图片归属后发布动态。 */
    @PostMapping
    public Result publishPost(@RequestBody CreatePostDTO request) {
        if (request == null) return Result.fail("动态参数不能为空");
        CampusPost post = new CampusPost();
        post.setActivityId(request.getActivityId());
        post.setTitle(request.getTitle());
        post.setImages(request.getImages());
        post.setContent(request.getContent());
        return campusPostService.publishPost(post);
    }

    /** 通过 Lua 原子切换点赞状态，并同步数据库计数。 */
    @PutMapping("/like/{id}")
    public Result togglePostLike(@PathVariable("id") Long id) {

        return campusPostService.togglePostLike(id);
    }

    /** 分页返回当前用户发布的动态。 */
    @GetMapping("/mine")
    public Result queryMyPosts(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        UserDTO user = UserHolder.getUser();

        Page<CampusPost> page = campusPostService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<CampusPost> records = page.getRecords();
        return Result.ok(records);
    }

    /** 分页返回指定用户发布的动态。 */
    @GetMapping("/by-user")
    public Result queryPostsByUser(
            @RequestParam("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<CampusPost> page = campusPostService.query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /** 分页返回指定活动下的动态。 */
    @GetMapping("/by-activity")
    public Result queryPostsByActivity(@RequestParam("id") Long activityId,
                                       @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return campusPostService.queryPostsByActivity(activityId, current);
    }

    /** 按点赞数倒序分页查询热门动态。 */
    @GetMapping("/hot")
    public Result queryHotPosts(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return campusPostService.queryHotPosts(current);
    }

    /** 查询动态详情并补充作者和当前用户点赞状态。 */
    @GetMapping("/{id}")
    public Result queryPostById(@PathVariable("id") Long id)
    {
        return campusPostService.queryPostById(id);
    }

    /** 按点赞时间返回前五位点赞用户。 */
    @GetMapping("/likes/{id}")
    public Result queryPostLikes(@PathVariable("id") Long id)
    {
        return campusPostService.queryPostLikes(id);
    }

}
