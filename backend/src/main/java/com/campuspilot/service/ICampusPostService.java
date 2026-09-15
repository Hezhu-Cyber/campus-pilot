package com.campuspilot.service;

import com.campuspilot.dto.Result;
import com.campuspilot.entity.CampusPost;
import com.baomidou.mybatisplus.extension.service.IService;

/** 定义校园动态的业务服务契约。 */
public interface ICampusPostService extends IService<CampusPost> {

    /** 查询动态详情并补充作者和当前用户点赞状态。 */
    Result queryPostById(Long id);

    /** 按点赞数倒序分页查询热门动态。 */
    Result queryHotPosts(Integer current);

    /** 通过 Lua 原子切换点赞状态，并同步数据库计数。 */
    Result togglePostLike(Long id);

    /** 按点赞时间返回前五位点赞用户。 */
    Result queryPostLikes(Long id);

    /** 校验内容和图片归属后发布动态。 */
    Result publishPost(CampusPost post);

    /** 分页返回指定活动下的动态。 */
    Result queryPostsByActivity(Long activityId, Integer current);
}
