package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.common.BizException;
import com.journalflow.dto.InteractionDtos.CommentRequest;
import com.journalflow.entity.*;
import com.journalflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 互动：评论（两级回复，软删除）、收藏、订阅、关注。
 */
@Service
@RequiredArgsConstructor
public class InteractionService {
    private final CommentMapper commentMapper;
    private final ManuscriptMapper manuscriptMapper;
    private final FavoriteMapper favoriteMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    // ---------------- 评论 ----------------

    public List<Map<String, Object>> comments(String targetType, Long targetId) {
        Manuscript manuscript = manuscriptMapper.selectById(targetId);
        if (!"MANUSCRIPT".equals(targetType) || manuscript == null
                || !"PUBLISHED".equals(manuscript.getStatus())) {
            throw BizException.notFound("文章不存在或未发布");
        }
        return commentMapper.selectVisibleByTarget(targetType, targetId);
    }

    @Transactional
    public Comment addComment(Long uid, CommentRequest req) {
        Manuscript m = manuscriptMapper.selectById(req.getManuscriptId());
        if (m == null || !"PUBLISHED".equals(m.getStatus())) {
            throw BizException.badParam("只能评论已出版的文章");
        }
        Long parentId = null;
        if (req.getParentId() != null) {
            Comment parent = commentMapper.selectById(req.getParentId());
            if (parent == null || parent.getStatus() != 1
                    || !"MANUSCRIPT".equals(parent.getTargetType())
                    || !req.getManuscriptId().equals(parent.getTargetId())) {
                throw BizException.badParam("回复的评论不存在");
            }
            if (parent.getParentId() != null) {
                throw BizException.badParam("仅支持两级回复，请直接回复顶级评论");
            }
            parentId = parent.getId();
        }
        Comment c = new Comment();
        c.setTargetType("MANUSCRIPT");
        c.setTargetId(req.getManuscriptId());
        c.setUserId(uid);
        c.setContent(req.getContent());
        c.setParentId(parentId);
        c.setStatus(1);
        c.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(c);
        // 通知文章作者与被回复人
        if (!m.getSubmitterId().equals(uid)) {
            notificationService.create(m.getSubmitterId(), "SYSTEM", "您的文章有新评论",
                    "《" + m.getTitle() + "》收到新评论。", m.getId());
        }
        if (parentId != null) {
            Comment parent = commentMapper.selectById(parentId);
            if (!parent.getUserId().equals(uid) && !parent.getUserId().equals(m.getSubmitterId())) {
                notificationService.create(parent.getUserId(), "SYSTEM", "您的评论有新回复",
                        "在《" + m.getTitle() + "》下有人回复了您的评论。", m.getId());
            }
        }
        return c;
    }

    @Transactional
    public void deleteComment(Long id, Long uid, boolean isEditorOrAdmin) {
        Comment c = commentMapper.selectById(id);
        if (c == null) throw BizException.notFound("评论不存在");
        if (!c.getUserId().equals(uid) && !isEditorOrAdmin) {
            throw BizException.forbidden("只能删除本人的评论");
        }
        c.setStatus(isEditorOrAdmin && !c.getUserId().equals(uid) ? 0 : 2);
        commentMapper.updateById(c);
    }

    // ---------------- 收藏 ----------------

    public void addFavorite(Long uid, Long manuscriptId) {
        Manuscript m = manuscriptMapper.selectById(manuscriptId);
        if (m == null || !"PUBLISHED".equals(m.getStatus())) {
            throw BizException.badParam("只能收藏已出版的文章");
        }
        if (favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).eq(Favorite::getManuscriptId, manuscriptId)) > 0) {
            return;
        }
        Favorite f = new Favorite();
        f.setUserId(uid);
        f.setManuscriptId(manuscriptId);
        f.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(f);
    }

    public void removeFavorite(Long uid, Long manuscriptId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).eq(Favorite::getManuscriptId, manuscriptId));
    }

    public List<Map<String, Object>> myFavorites(Long uid) {
        return favoriteMapper.selectMyFavorites(uid);
    }

    // ---------------- 订阅 ----------------

    public void addSubscription(Long uid, Long categoryId, String keyword) {
        if (categoryId == null && (keyword == null || keyword.isBlank())) {
            throw BizException.badParam("请至少选择订阅学科或填写关键词");
        }
        if (categoryId == null) {
            keyword = keyword.trim();
        }
        List<Subscription> existing = subscriptionMapper.selectList(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, uid)
                .eq(categoryId != null, Subscription::getCategoryId, categoryId)
                .eq(keyword != null, Subscription::getKeyword, keyword));
        if (!existing.isEmpty()) {
            throw BizException.badParam("已订阅过相同内容");
        }
        Subscription s = new Subscription();
        s.setUserId(uid);
        s.setCategoryId(categoryId);
        s.setKeyword(categoryId == null ? keyword : null);
        s.setCreatedAt(LocalDateTime.now());
        subscriptionMapper.insert(s);
    }

    public void removeSubscription(Long id, Long uid) {
        Subscription s = subscriptionMapper.selectById(id);
        if (s == null || !s.getUserId().equals(uid)) {
            throw BizException.notFound("订阅不存在");
        }
        subscriptionMapper.deleteById(id);
    }

    public List<Map<String, Object>> mySubscriptions(Long uid) {
        return subscriptionMapper.selectMySubscriptions(uid);
    }

    // ---------------- 关注 ----------------

    public void follow(Long uid, Long followeeId) {
        if (uid.equals(followeeId)) {
            throw BizException.badParam("不能关注自己");
        }
        if (userMapper.selectById(followeeId) == null) {
            throw BizException.notFound("用户不存在");
        }
        if (followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, uid).eq(Follow::getFolloweeId, followeeId)) > 0) {
            return;
        }
        Follow f = new Follow();
        f.setFollowerId(uid);
        f.setFolloweeId(followeeId);
        f.setCreatedAt(LocalDateTime.now());
        followMapper.insert(f);
    }

    public void unfollow(Long uid, Long followeeId) {
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, uid).eq(Follow::getFolloweeId, followeeId));
    }

    public List<Map<String, Object>> myFollowing(Long uid) {
        return followMapper.selectMyFollowing(uid);
    }

    // 通知服务延迟注入（避免循环依赖）
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;
}
