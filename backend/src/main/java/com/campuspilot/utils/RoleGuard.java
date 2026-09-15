package com.campuspilot.utils;

import com.campuspilot.dto.UserDTO;
import org.springframework.stereotype.Component;

/** 为 Controller 提供角色和资源归属权限校验。 */
@Component
public class RoleGuard {

    /** 要求当前用户为组织者或管理员。 */
    public void requireOrganizer() {
        UserDTO user = UserHolder.getUser();
        if (user == null || !("ORGANIZER".equals(user.getRole()) || "ADMIN".equals(user.getRole()))) {
            throw new SecurityException("只有活动组织者或管理员可以执行此操作");
        }
    }

    /** 要求当前用户为管理员。 */
    public void requireAdmin() {
        UserDTO user = UserHolder.getUser();
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new SecurityException("只有管理员可以执行此操作");
        }
    }

    /** 要求当前用户是资源所有者或管理员。 */
    public void requireOwnerOrAdmin(Long ownerId) {
        UserDTO user = UserHolder.getUser();
        if (user == null || (!("ADMIN".equals(user.getRole())) && !user.getId().equals(ownerId))) {
            throw new SecurityException("只能管理自己创建的活动");
        }
    }
}
