package com.campuspilot.assistant;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuspilot.assistant.dto.AssistantActivityDTO;
import com.campuspilot.assistant.dto.AssistantActionPrepareRequest;
import com.campuspilot.assistant.dto.AssistantPostDTO;
import com.campuspilot.assistant.dto.AssistantRegistrationPassDTO;
import com.campuspilot.assistant.dto.AssistantSupportTicketRequest;
import com.campuspilot.dto.RegistrationViewDTO;
import com.campuspilot.dto.Result;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.entity.Activity;
import com.campuspilot.entity.ActivityCategory;
import com.campuspilot.entity.CampusPost;
import com.campuspilot.entity.RegistrationPass;
import com.campuspilot.service.IActivityCategoryService;
import com.campuspilot.service.IActivityRegistrationService;
import com.campuspilot.service.IActivityService;
import com.campuspilot.service.ICampusPostService;
import com.campuspilot.service.IRegistrationPassService;
import com.campuspilot.support.SupportTicketService;
import com.campuspilot.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 仅允许 Python 助手服务通过内部令牌调用的内部工具接口。 */
@RestController
@RequestMapping("/internal/assistant/tools")
public class AssistantInternalController {

    private static final int MAX_TOOL_LIMIT = 10;

    @Resource
    private IActivityService activityService;

    @Resource
    private IActivityCategoryService categoryService;

    @Resource
    private IActivityRegistrationService registrationService;

    @Resource
    private ICampusPostService postService;

    @Resource
    private IRegistrationPassService registrationPassService;

    @Resource
    private AssistantActionService assistantActionService;

    @Resource
    private SupportTicketService supportTicketService;

    /** 按关键词、分类、价格和时间范围检索活动。 */
    @GetMapping("/activities/search")
    public List<AssistantActivityDTO> searchActivities(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "typeId", required = false) Long typeId,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        QueryWrapper<Activity> wrapper = new QueryWrapper<>();
        wrapper.eq("activity_status", "PUBLISHED");
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like("name", keyword).or().like("description", keyword));
        }
        if (typeId != null) {
            wrapper.eq("type_id", typeId);
        }
        if (maxPrice != null) {
            wrapper.le("avg_price", maxPrice);
        }
        LocalDateTime start = parseDateTime(startTime, false);
        LocalDateTime end = parseDateTime(endTime, true);
        if (start != null) {
            wrapper.ge("start_time", start);
        }
        if (end != null) {
            wrapper.le("start_time", end);
        }
        wrapper.orderByAsc("start_time");

        Page<Activity> page = activityService.page(new Page<>(1, normalizeLimit(limit)), wrapper);
        return page.getRecords().stream().map(this::toActivityDTO).collect(Collectors.toList());
    }

    /** 返回指定活动的精简详情。 */
    @GetMapping("/activities/{id}")
    public AssistantActivityDTO getActivity(@PathVariable("id") Long id) {
        Activity activity = activityService.getById(id);
        if (activity == null || !"PUBLISHED".equals(activity.getActivityStatus())) {
            throw new IllegalArgumentException("活动不存在");
        }
        return toActivityDTO(activity);
    }

    /** 返回全部活动分类，供模型理解校园场景。 */
    @GetMapping("/activity-categories")
    public List<ActivityCategory> listActivityCategories() {
        return categoryService.query().orderByAsc("sort").list();
    }

    /** 返回当前用户的报名记录。 */
    @GetMapping("/registrations/mine")
    @SuppressWarnings("unchecked")
    public List<RegistrationViewDTO> listMyRegistrations() {
        Object data = registrationService.queryMyRegistrations().getData();
        return data instanceof List ? (List<RegistrationViewDTO>) data : Collections.emptyList();
    }

    /** 返回当前热门校园动态。 */
    @GetMapping("/posts/hot")
    @SuppressWarnings("unchecked")
    public List<AssistantPostDTO> listHotPosts(
            @RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        Object data = postService.queryHotPosts(1).getData();
        if (!(data instanceof List)) {
            return Collections.emptyList();
        }
        List<CampusPost> posts = (List<CampusPost>) data;
        return posts.stream()
                .limit(normalizeLimit(limit))
                .map(this::toPostDTO)
                .collect(Collectors.toList());
    }

    /** 返回当前登录用户的最小安全摘要。 */
    @GetMapping("/profile")
    public UserDTO currentProfile() {
        return UserHolder.getUser();
    }

    /** 返回活动可用的报名凭证。 */
    @GetMapping("/activities/{id}/registration-passes")
    @SuppressWarnings("unchecked")
    public List<AssistantRegistrationPassDTO> listRegistrationPasses(@PathVariable("id") Long activityId) {
        Object data = registrationPassService.queryPassesByActivity(activityId).getData();
        if (!(data instanceof List)) {
            return Collections.emptyList();
        }
        Activity activity = activityService.getById(activityId);
        if (activity == null || !"PUBLISHED".equals(activity.getActivityStatus())) {
            return Collections.emptyList();
        }
        return ((List<RegistrationPass>) data).stream()
                .map(pass -> toPassDTO(pass, activity))
                .collect(Collectors.toList());
    }

    /** 为报名操作生成一次性确认令牌。 */
    @PostMapping("/actions/register/prepare")
    public Result prepareRegistration(@RequestBody AssistantActionPrepareRequest request) {
        if (request == null) {
            return Result.fail("缺少报名参数");
        }
        return assistantActionService.prepareRegistration(
                request.getActivityId(), request.getRegistrationPassId());
    }

    /** 为取消报名生成一次性确认令牌。 */
    @PostMapping("/actions/cancel/prepare")
    public Result prepareCancellation(@RequestBody AssistantActionPrepareRequest request) {
        return assistantActionService.prepareCancellation(
                request == null ? null : request.getRegistrationId());
    }

    /** 返回当前用户自己的客服工单。 */
    @GetMapping("/support-tickets/mine")
    public Result listMySupportTickets() {
        return supportTicketService.listMine(UserHolder.getUser().getId());
    }

    /** 为明确要求人工客服的用户创建可追踪工单。 */
    @PostMapping("/support-tickets")
    public Result createSupportTicket(@RequestBody AssistantSupportTicketRequest request) {
        if (request == null || StrUtil.isBlank(request.getContent())) {
            return Result.fail("工单内容不能为空");
        }
        Long ticketId = supportTicketService.create(
                UserHolder.getUser().getId(),
                request.getThreadId(),
                request.getCategory(),
                request.getSubject(),
                request.getContent());
        return Result.ok(ticketId);
    }

    private AssistantRegistrationPassDTO toPassDTO(RegistrationPass pass, Activity activity) {
        AssistantRegistrationPassDTO dto = BeanUtil.copyProperties(pass, AssistantRegistrationPassDTO.class);
        if (activity != null) {
            dto.setActivityName(activity.getName());
            dto.setAddress(activity.getAddress());
            dto.setStartTime(activity.getStartTime());
        }
        return dto;
    }
    private AssistantActivityDTO toActivityDTO(Activity activity) {
        return BeanUtil.copyProperties(activity, AssistantActivityDTO.class);
    }

    private AssistantPostDTO toPostDTO(CampusPost post) {
        return BeanUtil.copyProperties(post, AssistantPostDTO.class);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            try {
                LocalDate date = LocalDate.parse(value);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("时间参数格式错误");
            }
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 5;
        }
        return Math.min(limit, MAX_TOOL_LIMIT);
    }
}




