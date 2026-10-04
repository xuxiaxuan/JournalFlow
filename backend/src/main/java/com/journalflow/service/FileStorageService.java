package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.config.JournalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储：按年月分目录，记录 SHA-256 校验和。
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {
    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx", "tex", "zip");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final JournalProperties props;

    public record StoredFile(String path, String name, long size, String sha256) {}

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badParam("请上传稿件文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BizException.badParam("文件大小超出限制（最大 20MB）");
        }
        String original = sanitizeFileName(file.getOriginalFilename());
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badParam("不支持的文件类型，允许：" + ALLOWED_EXT);
        }
        validateFileSignature(file, ext);
        String subDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String stored = subDir + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = resolve(stored);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
            return new StoredFile(stored, original, file.getSize(), sha256(target));
        } catch (IOException e) {
            throw new BizException(com.journalflow.common.ErrorCode.INTERNAL, "文件保存失败");
        }
    }

    public Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw BizException.badParam("非法文件路径");
        }
        Path root = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        Path relative = Paths.get(storedPath);
        if (relative.isAbsolute()) {
            throw BizException.badParam("非法文件路径");
        }
        Path target = root.resolve(relative).normalize();
        if (target.equals(root) || !target.startsWith(root)) {
            throw BizException.badParam("非法文件路径");
        }
        return target;
    }

    public void deleteIfExists(String storedPath) {
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException e) {
            throw new BizException(com.journalflow.common.ErrorCode.INTERNAL, "文件清理失败");
        }
    }

    private String sanitizeFileName(String provided) {
        String normalized = provided == null ? "manuscript" : provided.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > MAX_FILE_NAME_LENGTH
                || name.chars().anyMatch(c -> Character.isISOControl(c))) {
            throw BizException.badParam("文件名不合法");
        }
        return name;
    }

    private void validateFileSignature(MultipartFile file, String ext) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(8);
            boolean valid = switch (ext) {
                case "pdf" -> startsWith(head, new int[]{0x25, 0x50, 0x44, 0x46, 0x2D}); // %PDF-
                case "doc" -> startsWith(head, new int[]{0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1});
                case "docx", "zip" -> startsWith(head, new int[]{0x50, 0x4B, 0x03, 0x04})
                        || startsWith(head, new int[]{0x50, 0x4B, 0x05, 0x06})
                        || startsWith(head, new int[]{0x50, 0x4B, 0x07, 0x08});
                case "tex" -> head.length > 0 && java.util.stream.IntStream.range(0, head.length)
                        .noneMatch(i -> head[i] == 0);
                default -> false;
            };
            if (!valid) throw BizException.badParam("文件内容与扩展名不匹配");
        } catch (IOException e) {
            throw new BizException(com.journalflow.common.ErrorCode.INTERNAL, "文件读取失败");
        }
    }

    private boolean startsWith(byte[] data, int[] signature) {
        if (data.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xff) != signature[i]) return false;
        }
        return true;
    }

    private String sha256(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException(com.journalflow.common.ErrorCode.INTERNAL, "校验和计算失败");
        }
    }
}
