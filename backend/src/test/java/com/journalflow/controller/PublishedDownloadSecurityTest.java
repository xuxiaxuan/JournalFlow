package com.journalflow.controller;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.ManuscriptVersion;
import com.journalflow.mapper.ManuscriptMapper;
import com.journalflow.mapper.ManuscriptVersionMapper;
import com.journalflow.mapper.ViewStatMapper;
import com.journalflow.service.FileStorageService;
import com.journalflow.service.InteractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishedDownloadSecurityTest {
    @Mock InteractionService interactionService;
    @Mock FileStorageService fileStorage;
    @Mock ManuscriptVersionMapper versionMapper;
    @Mock ManuscriptMapper manuscriptMapper;
    @Mock ViewStatMapper viewStatMapper;
    @InjectMocks InteractionController controller;
    @TempDir Path tempDir;

    @Test
    void historicalSubmissionVersionIsNotPubliclyDownloadable() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript(10L));

        BizException error = assertThrows(BizException.class,
                () -> controller.downloadPublished(8L, 9L));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
        verify(versionMapper, never()).selectById(9L);
    }

    @Test
    void inconsistentCurrentVersionOwnershipIsRejected() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript(10L));
        ManuscriptVersion version = version(10L, 99L);
        when(versionMapper.selectById(10L)).thenReturn(version);

        BizException error = assertThrows(BizException.class,
                () -> controller.downloadPublished(8L, 10L));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void currentPublishedVersionCanBeDownloadedAndCounted() throws Exception {
        byte[] content = "%PDF-1.7 public".getBytes();
        Path stored = tempDir.resolve("paper.pdf");
        Files.write(stored, content);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript(10L));
        ManuscriptVersion version = version(10L, 8L);
        when(versionMapper.selectById(10L)).thenReturn(version);
        when(fileStorage.resolve("2026-09/paper.pdf")).thenReturn(stored);

        var response = controller.downloadPublished(8L, null);

        assertArrayEquals(content, response.getBody().getInputStream().readAllBytes());
        verify(viewStatMapper).incrementDownload(8L, 1);
    }

    private Manuscript manuscript(Long currentVersionId) {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(8L);
        manuscript.setCurrentVersionId(currentVersionId);
        manuscript.setStatus("PUBLISHED");
        return manuscript;
    }

    private ManuscriptVersion version(Long id, Long manuscriptId) {
        ManuscriptVersion version = new ManuscriptVersion();
        version.setId(id);
        version.setManuscriptId(manuscriptId);
        version.setFilePath("2026-09/paper.pdf");
        version.setFileName("paper.pdf");
        return version;
    }
}
