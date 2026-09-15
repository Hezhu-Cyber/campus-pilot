package com.campuspilot;

import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.campuspilot.entity.UserInfo;
import com.campuspilot.utils.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证密码编码器的正确匹配、错误拒绝和长度限制。 */
public class CampusPilotApplicationTests {

    /** 验证新编码的密码可被正确原文匹配。 */
    @Test
    void passwordEncoderMatchesEncodedPassword() {
        String encoded = PasswordEncoder.encode("campus-pilot");
        assertTrue(PasswordEncoder.matches(encoded, "campus-pilot"));
    }

    /** 验证错误密码不会通过哈希校验。 */
    @Test
    void passwordEncoderRejectsWrongPassword() {
        String encoded = PasswordEncoder.encode("campus-pilot");
        assertFalse(PasswordEncoder.matches(encoded, "wrong-password"));
    }

    /** 验证过短密码会被编码器拒绝。 */
    @Test
    void passwordEncoderRequiresMinimumLength() {
        assertThrows(IllegalArgumentException.class, () -> PasswordEncoder.encode("short"));
    }

    /** 新用户资料使用用户 ID 作为输入主键，插入时不能被当成数据库自增列省略。 */
    @Test
    void userInfoUsesInputUserId() throws Exception {
        TableId tableId = UserInfo.class.getDeclaredField("userId").getAnnotation(TableId.class);
        assertEquals(IdType.INPUT, tableId.type());
    }

}
