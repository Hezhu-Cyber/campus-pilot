package com.campuspilot.assistant;

import com.campuspilot.entity.SupportTicket;
import com.campuspilot.mapper.SupportTicketMapper;
import com.campuspilot.support.SupportTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证转人工工单的用户归属和初始状态。 */
class SupportTicketServiceTest {

    @Test
    void createsOpenTicketForCurrentUser() {
        SupportTicketMapper mapper = mock(SupportTicketMapper.class);
        when(mapper.insert(any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket ticket = invocation.getArgument(0);
            ticket.setId(88L);
            return 1;
        });
        SupportTicketService service = new SupportTicketService();
        ReflectionTestUtils.setField(service, "supportTicketMapper", mapper);

        Long id = service.create(7L, "thread-1", "COMPLAINT", "投诉报名问题", "报名后没有显示记录");

        assertEquals(88L, id);
        verify(mapper).insert(argThat(ticket ->
                ticket.getUserId().equals(7L)
                        && "OPEN".equals(ticket.getStatus())
                        && "thread-1".equals(ticket.getThreadId())));
    }
}
