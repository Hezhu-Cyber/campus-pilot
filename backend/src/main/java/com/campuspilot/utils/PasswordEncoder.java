package com.campuspilot.utils;

import cn.hutool.core.util.RandomUtil;
import org.springframework.util.DigestUtils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用 PBKDF2 为密码加盐派生并兼容旧版哈希校验。 */
public class PasswordEncoder {

    /** PBKDF2 的迭代次数。 */
    private static final int ITERATIONS = 120000;

    /** PBKDF2 派生密钥位数。 */
    private static final int KEY_LENGTH = 256;

    /** 为每个新密码生成不可预测的独立盐值。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 对原始密码生成随机盐并计算 PBKDF2 哈希。 */
    public static String encode(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码至少需要8位");
        }
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /** 按旧项目的 salt@MD5 格式重算哈希，仅用于平滑迁移旧密码。 */
    private static String encode(String password, String salt) {

        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }

    /** 使用常量时间比较校验原始密码，并兼容旧 SHA-256 格式。 */
    public static Boolean matches(String encodedPassword, String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith("pbkdf2$")) {
            try {
                String[] parts = encodedPassword.split("\\$");
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                return MessageDigest.isEqual(expected, pbkdf2(rawPassword.toCharArray(), salt, iterations));
            } catch (RuntimeException e) {
                return false;
            }
        }

        if(!encodedPassword.contains("@")) return false;
        String[] arr = encodedPassword.split("@", 2);

        String salt = arr[0];

        return encodedPassword.equals(encode(rawPassword, salt));
    }

    /** 执行 PBKDF2WithHmacSHA256 密钥派生。 */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算密码摘要", e);
        }
    }
}
