package com.campuspilot.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campuspilot.dto.Result;
import com.campuspilot.entity.CampusPost;
import com.campuspilot.entity.CampusPostComment;
import com.campuspilot.entity.User;
import com.campuspilot.mapper.CampusPostCommentMapper;
import com.campuspilot.service.ICampusPostCommentService;
import com.campuspilot.service.ICampusPostService;
import com.campuspilot.service.IUserService;
import com.campuspilot.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 实现动态评论的业务规则与持久化协调。 */
@Service
public class CampusPostCommentServiceImpl
        extends ServiceImpl<CampusPostCommentMapper, CampusPostComment>
        implements ICampusPostCommentService {

    @Resource
    private ICampusPostService postService;
    @Resource
    private IUserService userService;

    /** 分页查询评论并补充评论者信息。 */
    @Override
    public Result listByPost(Long postId, Integer current) {
        Page<CampusPostComment> page = query().eq("post_id", postId).eq("status", 0)
                .orderByDesc("create_time").page(new Page<>(Math.max(current, 1), 10));
        List<CampusPostComment> comments = page.getRecords();
        List<Long> userIds = comments.stream().map(CampusPostComment::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> users = userIds.isEmpty() ? java.util.Collections.emptyMap()
                : userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        comments.forEach(comment -> {
            User user = users.get(comment.getUserId());
            if (user != null) {
                comment.setUserName(user.getNickName());
                comment.setUserIcon(user.getIcon());
            }
        });
        return Result.ok(comments, page.getTotal());
    }

    /** 校验内容后创建评论，并递增动态评论数。 */
    @Override
    @Transactional
    public Result addComment(Long postId, String content) {
        if (StrUtil.isBlank(content) || content.trim().length() > 500) {
            return Result.fail("评论内容应为1到500个字符");
        }
        CampusPost post = postService.getById(postId);
        if (post == null) return Result.fail("动态不存在");
        CampusPostComment comment = new CampusPostComment();
        comment.setPostId(postId);
        comment.setUserId(UserHolder.getUser().getId());
        comment.setParentId(0L);
        comment.setAnswerId(0L);
        comment.setContent(content.trim());
        comment.setLiked(0);
        comment.setStatus(false);
        save(comment);
        postService.update().setSql("comments = IFNULL(comments, 0) + 1").eq("id", postId).update();
        return Result.ok(comment.getId());
    }

    /** 校验作者或管理员权限后删除评论并递减计数。 */
    @Override
    @Transactional
    public Result deleteComment(Long commentId) {
        CampusPostComment comment = getById(commentId);
        if (comment == null || !UserHolder.getUser().getId().equals(comment.getUserId())) {
            return Result.fail("评论不存在或无权删除");
        }
        removeById(commentId);
        postService.update().setSql("comments = GREATEST(IFNULL(comments, 0) - 1, 0)")
                .eq("id", comment.getPostId()).update();
        return Result.ok();
    }
}
