package com.journalflow.service;

import com.fasterxml.jackson.databind.node.*;
import com.journalflow.common.BizException;
import com.journalflow.entity.*;
import com.journalflow.mapper.*;
import com.journalflow.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResearchToolsServiceTest {
    @Mock ManuscriptService manuscripts;
    @Mock ManuscriptMapper manuscriptMapper;
    @Mock ManuscriptVersionMapper versions;
    @Mock ReviewAssignmentMapper assignments;
    @Mock ReviewResponseMapper responses;
    @Mock LlmTaskMapper tasks;
    @Mock ManuscriptTextExtractor extractor;
    @InjectMocks ResearchToolsService service;
    UserDetailsImpl person(long id, String role) { return UserDetailsImpl.of(id, "test", "unused", 1, Set.of(role)); }
    Manuscript manuscript() { Manuscript m = new Manuscript(); m.setId(1L); m.setSubmitterId(8L); m.setStatus("PUBLISHED"); return m; }

    @Test void diffKeepsCommonLinesAndSeparatesChangedLines() {
        var rows = ResearchToolsService.diff("共同标题\n原有实验\n结论", "共同标题\n补充实验\n结论\n附录");
        assertEquals(List.of("SAME", "REMOVED", "ADDED", "SAME", "ADDED"), rows.stream().map(r -> r.get("type")).toList());
        assertEquals(2, rows.get(1).get("oldLine")); assertEquals(2, rows.get(2).get("newLine"));
    }
    @Test void diffHasBoundedOutputForVeryLargeParagraphCounts() {
        assertEquals(400, ResearchToolsService.diff("", "line\n".repeat(1000)).size());
        assertTrue(ResearchToolsService.diff("", "").isEmpty());
    }
    @Test void evidenceSeparatesBodySnapshotAndUnverifiedClaims() {
        ManuscriptVersion version = new ManuscriptVersion(); version.setTitle("快照标题");
        assertEquals("BODY", ResearchToolsService.locate("title", TextNode.valueOf("真实正文"), "第一行\n真实 正文", version).get("source"));
        assertEquals(2, ResearchToolsService.locate("title", TextNode.valueOf("真实正文"), "第一行\n真实 正文", version).get("line"));
        assertEquals("VERSION_METADATA", ResearchToolsService.locate("title", TextNode.valueOf("快照标题"), "其他内容", version).get("source"));
        assertEquals("UNVERIFIED", ResearchToolsService.locate("title", TextNode.valueOf("未出现的结论"), "其他内容", version).get("source"));
        assertEquals("UNVERIFIED", ResearchToolsService.locate("word_count_est", IntNode.valueOf(10), "10", version).get("source"));
    }
    @Test void publishedStatusDoesNotExposeToolsToUnrelatedReaders() {
        when(manuscriptMapper.selectById(1L)).thenReturn(manuscript());
        when(assignments.selectCount(any())).thenReturn(0L);
        assertThrows(BizException.class, () -> service.compare(1L, 10L, 11L, person(99, "AUTHOR")));
        verifyNoInteractions(versions, extractor);
    }
    @Test void comparisonRejectsVersionsFromOtherManuscripts() {
        when(manuscriptMapper.selectById(1L)).thenReturn(manuscript());
        ManuscriptVersion wrong = new ManuscriptVersion(); wrong.setManuscriptId(2L);
        when(versions.selectById(10L)).thenReturn(wrong);
        assertThrows(BizException.class, () -> service.compare(1L, 10L, 11L, person(8, "AUTHOR")));
        verifyNoInteractions(extractor);
    }
    @Test void authorCannotReadEditorialAiEvidence() {
        assertThrows(BizException.class, () -> service.evidence(1L, person(8, "AUTHOR")));
        verifyNoInteractions(tasks);
    }
    @Test void unpublishedReviewCommentsDoNotProduceAVisibleChecklist() {
        when(manuscriptMapper.selectById(1L)).thenReturn(manuscript());
        var author = person(8, "AUTHOR");
        when(manuscripts.detail(1L, author)).thenReturn(Map.of());
        assertTrue(service.checklist(1L, author).isEmpty());
        verifyNoInteractions(responses);
    }
}
