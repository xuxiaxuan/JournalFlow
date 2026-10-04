package com.journalflow.controller;

import com.journalflow.common.OperationLog;
import com.journalflow.common.PageVO;
import com.journalflow.common.Pagination;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.entity.Notification;
import com.journalflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public Result<PageVO<Notification>> my(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Pagination.validate(page, size);
        return Result.ok(notificationService.my(SecurityUtils.currentUserId(), page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount(SecurityUtils.currentUserId())));
    }

    @PutMapping("/{id}/read")
    @OperationLog(action = "NOTIFICATION_READ", objectType = "NOTIFICATION", objectIdParam = "{id}")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @PutMapping("/read-all")
    @OperationLog(action = "NOTIFICATION_READ_ALL", objectType = "NOTIFICATION")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(SecurityUtils.currentUserId());
        return Result.ok();
    }
}
