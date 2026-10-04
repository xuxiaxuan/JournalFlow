package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.entity.Notification;
import com.journalflow.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceSecurityTest {
    @Mock NotificationMapper notificationMapper;
    @InjectMocks NotificationService notificationService;

    @Test
    void userCannotMarkAnotherUsersNotificationAsRead() {
        Notification notification = notification(7L, 202L);
        when(notificationMapper.selectById(7L)).thenReturn(notification);

        BizException error = assertThrows(BizException.class,
                () -> notificationService.markRead(7L, 101L));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
        assertNull(notification.getReadAt());
    }

    @Test
    void ownerCanMarkNotificationAsRead() {
        Notification notification = notification(7L, 101L);
        when(notificationMapper.selectById(7L)).thenReturn(notification);

        notificationService.markRead(7L, 101L);

        assertNotNull(notification.getReadAt());
        verify(notificationMapper).updateById(notification);
    }

    private Notification notification(Long id, Long userId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(userId);
        return notification;
    }
}
