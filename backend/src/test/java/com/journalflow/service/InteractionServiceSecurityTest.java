package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.dto.InteractionDtos.CommentRequest;
import com.journalflow.entity.Comment;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.Subscription;
import com.journalflow.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionServiceSecurityTest {
    @Mock CommentMapper commentMapper;
    @Mock ManuscriptMapper manuscriptMapper;
    @Mock FavoriteMapper favoriteMapper;
    @Mock SubscriptionMapper subscriptionMapper;
    @Mock FollowMapper followMapper;
    @Mock UserMapper userMapper;
    @Mock NotificationService notificationService;
    @InjectMocks InteractionService interactionService;

    @Test
    void commentsAreLimitedToPublishedManuscripts() {
        Manuscript manuscript = new Manuscript();
        manuscript.setStatus("ACCEPTED");
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);

        BizException error = assertThrows(BizException.class,
                () -> interactionService.addComment(101L, commentRequest(8L, null)));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void commentsOfWithdrawnArticleAreNotPubliclyReadable() {
        Manuscript manuscript = new Manuscript();
        manuscript.setStatus("WITHDRAWN");
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);

        BizException error = assertThrows(BizException.class,
                () -> interactionService.comments("MANUSCRIPT", 8L));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void commentsOfPublishedArticleRemainReadable() {
        Manuscript manuscript = new Manuscript();
        manuscript.setStatus("PUBLISHED");
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(commentMapper.selectVisibleByTarget("MANUSCRIPT", 8L)).thenReturn(List.of());

        assertEquals(List.of(), interactionService.comments("MANUSCRIPT", 8L));
        verify(commentMapper).selectVisibleByTarget("MANUSCRIPT", 8L);
    }

    @Test
    void thirdLevelReplyIsRejected() {
        Manuscript manuscript = new Manuscript();
        manuscript.setStatus("PUBLISHED");
        Comment parent = new Comment();
        parent.setId(5L);
        parent.setStatus(1);
        parent.setTargetType("MANUSCRIPT");
        parent.setTargetId(8L);
        parent.setParentId(4L);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(commentMapper.selectById(5L)).thenReturn(parent);

        BizException error = assertThrows(BizException.class,
                () -> interactionService.addComment(101L, commentRequest(8L, 5L)));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void ordinaryUserCannotDeleteAnotherUsersComment() {
        Comment comment = new Comment();
        comment.setId(5L);
        comment.setUserId(202L);
        when(commentMapper.selectById(5L)).thenReturn(comment);

        BizException error = assertThrows(BizException.class,
                () -> interactionService.deleteComment(5L, 101L, false));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void editorModerationUsesHiddenStatus() {
        Comment comment = new Comment();
        comment.setId(5L);
        comment.setUserId(202L);
        comment.setStatus(1);
        when(commentMapper.selectById(5L)).thenReturn(comment);

        interactionService.deleteComment(5L, 301L, true);

        assertEquals(0, comment.getStatus());
        verify(commentMapper).updateById(comment);
    }

    @Test
    void subscriptionRequiresCategoryOrKeyword() {
        BizException error = assertThrows(BizException.class,
                () -> interactionService.addSubscription(101L, null, "  "));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void duplicateSubscriptionIsRejected() {
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(new Subscription()));

        BizException error = assertThrows(BizException.class,
                () -> interactionService.addSubscription(101L, null, "大模型"));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void userCannotFollowSelf() {
        BizException error = assertThrows(BizException.class,
                () -> interactionService.follow(101L, 101L));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    private CommentRequest commentRequest(Long manuscriptId, Long parentId) {
        CommentRequest request = new CommentRequest();
        request.setManuscriptId(manuscriptId);
        request.setParentId(parentId);
        request.setContent("有价值的研究");
        return request;
    }
}
