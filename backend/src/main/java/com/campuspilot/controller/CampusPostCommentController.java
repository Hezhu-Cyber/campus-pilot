package com.campuspilot.controller;

import com.campuspilot.dto.Result;
import com.campuspilot.service.ICampusPostCommentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/** 提供动态评论相关的 HTTP 接口。 */
@RestController
@RequestMapping("/post-comments")
public class CampusPostCommentController {
    @Resource
    private ICampusPostCommentService commentService;

    /** 分页返回指定动态的评论。 */
    @GetMapping("/post/{postId}")
    public Result list(@PathVariable Long postId,
                       @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return commentService.listByPost(postId, current);
    }

    /** 向指定动态添加评论。 */
    @PostMapping("/post/{postId}")
    public Result add(@PathVariable Long postId, @RequestBody Map<String, String> body) {
        return commentService.addComment(postId, body.get("content"));
    }

    /** 删除当前用户有权管理的评论。 */
    @DeleteMapping("/{commentId}")
    public Result delete(@PathVariable Long commentId) {
        return commentService.deleteComment(commentId);
    }
}
