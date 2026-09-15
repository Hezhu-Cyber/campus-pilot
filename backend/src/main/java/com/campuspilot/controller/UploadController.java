package com.campuspilot.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.campuspilot.dto.Result;
import com.campuspilot.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** 提供用户动态图片的安全上传与删除接口。 */
@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /** 单张图片允许的最大字节数。 */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 图片允许的最大宽度。 */
    private static final int MAX_IMAGE_WIDTH = 10000;

    /** 图片允许的最大高度。 */
    private static final int MAX_IMAGE_HEIGHT = 10000;

    /** 图片允许的最大总像素数，用于防止解压炸弹。 */
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;

    /** 允许上传的图片扩展名白名单。 */
    private static final Set<String> ALLOWED_SUFFIXES = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif"));

    @Value("${app.upload-dir:../frontend/imgs}")
    private String uploadDir;

    /** 校验图片后存入当前用户的隔离目录。 */
    @PostMapping("post")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            validateImage(image);

            String originalFilename = image.getOriginalFilename();

            String fileName = createNewFileName(originalFilename);
            // canonical path 检查用于拦截 ../ 等路径穿越手法。
            File root = new File(uploadDir).getCanonicalFile();
            if (!root.exists() && !root.mkdirs()) throw new IOException("无法创建上传目录");
            File target = new File(root, fileName).getCanonicalFile();
            if (!target.toPath().startsWith(root.toPath())) throw new IllegalArgumentException("错误的文件名称");
            File parent = target.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) throw new IOException("无法创建图片目录");
            image.transferTo(target);

            log.debug("文件上传成功，{}", fileName);
            return Result.ok("/" + fileName.replace('\\', '/'));
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /** 校验路径和归属后删除当前用户上传的图片。 */
    @DeleteMapping("/post")
    public Result deleteCampusPostImg(@RequestParam("name") String filename) {
        try {
            String relativeName = filename.replace('\\', '/').replaceFirst("^/", "");
            File root = new File(uploadDir).getCanonicalFile();
            File file = new File(root, relativeName).getCanonicalFile();
            String ownedPrefix = "posts/" + UserHolder.getUser().getId() + "/";
            if (!relativeName.startsWith(ownedPrefix) || !file.toPath().startsWith(root.toPath()) || file.isDirectory()) {
                return Result.fail("错误的文件名称");
            }
            return FileUtil.del(file) ? Result.ok() : Result.fail("图片不存在或删除失败");
        } catch (IOException e) {
            return Result.fail("删除图片失败");
        }
    }

    /** 使用 UUID 生成无冲突文件名并保留安全后缀。 */
    private String createNewFileName(String originalFilename) {

        String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase(Locale.ROOT);

        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        Long userId = UserHolder.getUser().getId();
        return StrUtil.format("posts/{}/{}/{}/{}.{}", userId, d1, d2, name, suffix);
    }

    /** 检查图片大小、扩展名、解码结果和像素上限。 */
    private void validateImage(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("图片不能超过5MB");
        }
        String original = image.getOriginalFilename();
        String suffix = StrUtil.subAfter(original, ".", true);
        if (StrUtil.isBlank(suffix) || !ALLOWED_SUFFIXES.contains(suffix.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("仅支持 JPG、PNG 和 GIF 图片");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("文件内容不是有效图片");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(image.getInputStream())) {
            if (input == null) throw new IllegalArgumentException("无法读取图片内容");
            // 不相信 Content-Type 或扩展名，使用 ImageIO 实际解析文件头和尺寸。
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("文件内容不是有效图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("图片尺寸过大或无效");
                }
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean matches = ("jpg".equals(suffix) || "jpeg".equals(suffix))
                        ? ("jpg".equals(format) || "jpeg".equals(format)) : suffix.equals(format);
                if (!matches) throw new IllegalArgumentException("图片扩展名与实际格式不一致");
            } finally {
                reader.dispose();
            }
        }
    }
}
