package com.campuspilot.assistant;

import com.campuspilot.assistant.dto.AssistantActionConfirmRequest;
import com.campuspilot.assistant.dto.AssistantChatRequest;
import com.campuspilot.dto.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/** 面向登录用户的智能助手入口。 */
@RestController
@RequestMapping("/assistant")
public class AssistantController {

    @Resource
    private IAssistantService assistantService;

    @Resource
    private AssistantActionService assistantActionService;

    /** 进入统一登录校验后，将本轮问题交给 Python 助手服务。 */
    @PostMapping("/chat")
    public Result chat(@RequestBody AssistantChatRequest request) {
        return assistantService.chat(request);
    }

    /** 用户明确确认后执行幂等助手操作。 */
    @PostMapping("/actions/confirm")
    public Result confirmAction(@RequestBody AssistantActionConfirmRequest request) {
        return assistantActionService.confirm(request == null ? null : request.getToken());
    }

    /** 查询确认动作的最终或处理中状态。 */
    @PostMapping("/actions/status")
    public Result actionStatus(@RequestBody AssistantActionConfirmRequest request) {
        return assistantActionService.status(request == null ? null : request.getToken());
    }
}
