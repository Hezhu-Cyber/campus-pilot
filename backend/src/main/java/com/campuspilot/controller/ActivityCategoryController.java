package com.campuspilot.controller;

import com.campuspilot.dto.Result;
import com.campuspilot.entity.ActivityCategory;
import com.campuspilot.service.IActivityCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/** 提供活动分类相关的 HTTP 接口。 */
@RestController
@RequestMapping("/activity-category")
public class ActivityCategoryController {
    @Resource
    private IActivityCategoryService typeService;

    /** 按排序字段返回全部活动分类。 */
    @GetMapping("list")
    public Result queryTypeList() {
        List<ActivityCategory> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
