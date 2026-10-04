package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.config.JournalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceSecurityTest {
    @TempDir Path uploadDir;
    private FileStorageService fileStorage;

    @BeforeEach
    void setUp() {
        JournalProperties properties = new JournalProperties();
        properties.setUploadDir(uploadDir.toString());
        fileStorage = new FileStorageService(properties);
    }

    @Test
    void rejectsEmptyAndUnsupportedFiles() {
        MockMultipartFile empty = new MockMultipartFile("file", "paper.pdf", "application/pdf", new byte[0]);
        assertEquals(ErrorCode.BAD_PARAM,
                assertThrows(BizException.class, () -> fileStorage.store(empty)).getErrorCode());

        MockMultipartFile executable = new MockMultipartFile(
                "file", "payload.exe", "application/octet-stream", new byte[]{1, 2, 3});
        assertEquals(ErrorCode.BAD_PARAM,
                assertThrows(BizException.class, () -> fileStorage.store(executable)).getErrorCode());
    }

    @Test
    void enforcesSizeLimitInsideStorageService() {
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        BizException error = assertThrows(BizException.class, () -> fileStorage.store(oversized));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
        assertEquals("文件大小超出限制（最大 20MB）", error.getMessage());
    }

    @Test
    void stripsClientPathAndStoresChecksum() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../private/paper.pdf", "application/pdf", "%PDF-1.7 demo".getBytes());

        FileStorageService.StoredFile stored = fileStorage.store(file);

        assertEquals("paper.pdf", stored.name());
        assertEquals(64, stored.sha256().length());
        assertTrue(fileStorage.resolve(stored.path()).startsWith(uploadDir.toAbsolutePath()));
        assertTrue(java.nio.file.Files.exists(fileStorage.resolve(stored.path())));
    }

    @Test
    void rejectsExecutableContentDisguisedAsPdf() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "paper.pdf", "application/pdf", new byte[]{0x4d, 0x5a, 1, 2, 3, 4});

        BizException error = assertThrows(BizException.class, () -> fileStorage.store(disguised));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
        assertEquals("文件内容与扩展名不匹配", error.getMessage());
    }

    @Test
    void rejectsTraversalBlankAndAbsolutePaths() {
        assertEquals(ErrorCode.BAD_PARAM,
                assertThrows(BizException.class, () -> fileStorage.resolve("../outside.pdf")).getErrorCode());
        assertEquals(ErrorCode.BAD_PARAM,
                assertThrows(BizException.class, () -> fileStorage.resolve(" ")).getErrorCode());
        assertEquals(ErrorCode.BAD_PARAM,
                assertThrows(BizException.class,
                        () -> fileStorage.resolve(uploadDir.resolve("paper.pdf").toString())).getErrorCode());
    }

    @Test
    void storedFileCanBeSafelyCleanedAfterRollback() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "paper.pdf", "application/pdf", "%PDF-1.7 rollback".getBytes());
        FileStorageService.StoredFile stored = fileStorage.store(file);
        Path path = fileStorage.resolve(stored.path());
        assertTrue(java.nio.file.Files.exists(path));

        fileStorage.deleteIfExists(stored.path());

        assertFalse(java.nio.file.Files.exists(path));
    }
}
