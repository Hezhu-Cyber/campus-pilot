package com.campuspilot.service;

import com.campuspilot.entity.CampusPostComment;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campuspilot.dto.Result;

/** 定义动态评论的业务服务契约。 */
public interface ICampusPostCommentService extends IService<CampusPostComment> {

    /** 分页查询评论并补充评论者信息。 */
    Result listByPost(Long postId, Integer current);

    /** 校验内容后创建评论，并递增动态评论数。 */
    Result addComment(Long postId, String content);

    /** 校验作者或管理员权限后删除评论并递减计数。 */
    Result deleteComment(Long commentId);
}
