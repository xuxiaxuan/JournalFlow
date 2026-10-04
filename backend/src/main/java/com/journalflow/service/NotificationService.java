package com.journalflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.journalflow.common.PageVO;
import com.journalflow.entity.Notification;
import com.journalflow.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationMapper notificationMapper;

    public void create(Long userId, String type, String title, String content, Long refId) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    public PageVO<Notification> my(Long userId, long page, long size, boolean unreadOnly) {
        Page<Notification> p = new Page<>(page, size);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(unreadOnly, Notification::getReadAt, (LocalDateTime) null)
                .orderByDesc(Notification::getCreatedAt);
        Page<Notification> result = notificationMapper.selectPage(p, wrapper);
        return PageVO.of(result.getRecords(), result.getTotal(), page, size);
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .isNull(Notification::getReadAt));
    }

    public void markRead(Long id, Long userId) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw com.journalflow.common.BizException.notFound("消息不存在");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(n);
        }
    }

    public void markAllRead(Long userId) {
        notificationMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId).isNull(Notification::getReadAt))
                .forEach(n -> {
                    n.setReadAt(LocalDateTime.now());
                    notificationMapper.updateById(n);
                });
    }
}
