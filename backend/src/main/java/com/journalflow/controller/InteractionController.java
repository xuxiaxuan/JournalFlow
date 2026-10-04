package com.journalflow.controller;

import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.common.OperationLog;
import com.journalflow.dto.InteractionDtos.CommentRequest;
import com.journalflow.dto.InteractionDtos.FavoriteRequest;
import com.journalflow.dto.InteractionDtos.SubscriptionRequest;
import com.journalflow.entity.Comment;
import com.journalflow.service.InteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InteractionController {
    private final InteractionService interactionService;
    private final com.journalflow.service.FileStorageService fileStorage;
    private final com.journalflow.mapper.ManuscriptVersionMapper versionMapper;
    private final com.journalflow.mapper.ManuscriptMapper manuscriptMapper;
    private final com.journalflow.mapper.ViewStatMapper viewStatMapper;

    // ---------------- 评论 ----------------

    @GetMapping("/published/{id}/comments")
    public Result<List<Map<String, Object>>> comments(@PathVariable Long id) {
        return Result.ok(interactionService.comments("MANUSCRIPT", id));
    }

    @PostMapping("/comments")
    @OperationLog(action = "COMMENT_CREATE", objectType = "COMMENT")
    public Result<Comment> addComment(@Valid @RequestBody CommentRequest req) {
        return Result.ok(interactionService.addComment(SecurityUtils.currentUserId(), req));
    }

    @DeleteMapping("/comments/{id}")
    @OperationLog(action = "COMMENT_DELETE", objectType = "COMMENT", objectIdParam = "{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        interactionService.deleteComment(id, SecurityUtils.currentUserId(),
                SecurityUtils.hasRole("EDITOR") || SecurityUtils.hasRole("ADMIN"));
        return Result.ok();
    }

    // ---------------- 收藏 ----------------

    @PostMapping("/favorites")
    @OperationLog(action = "FAVORITE_ADD", objectType = "MANUSCRIPT")
    public Result<Void> addFavorite(@Valid @RequestBody FavoriteRequest req) {
        interactionService.addFavorite(SecurityUtils.currentUserId(), req.getManuscriptId());
        return Result.ok();
    }

    @DeleteMapping("/favorites/{manuscriptId}")
    @OperationLog(action = "FAVORITE_REMOVE", objectType = "MANUSCRIPT", objectIdParam = "{manuscriptId}")
    public Result<Void> removeFavorite(@PathVariable Long manuscriptId) {
        interactionService.removeFavorite(SecurityUtils.currentUserId(), manuscriptId);
        return Result.ok();
    }

    @GetMapping("/favorites/my")
    public Result<List<Map<String, Object>>> myFavorites() {
        return Result.ok(interactionService.myFavorites(SecurityUtils.currentUserId()));
    }

    // ---------------- 订阅 ----------------

    @PostMapping("/subscriptions")
    @OperationLog(action = "SUBSCRIPTION_ADD", objectType = "SUBSCRIPTION")
    public Result<Void> addSubscription(@Valid @RequestBody SubscriptionRequest req) {
        interactionService.addSubscription(SecurityUtils.currentUserId(), req.getCategoryId(), req.getKeyword());
        return Result.ok();
    }

    @DeleteMapping("/subscriptions/{id}")
    @OperationLog(action = "SUBSCRIPTION_REMOVE", objectType = "SUBSCRIPTION", objectIdParam = "{id}")
    public Result<Void> removeSubscription(@PathVariable Long id) {
        interactionService.removeSubscription(id, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @GetMapping("/subscriptions/my")
    public Result<List<Map<String, Object>>> mySubscriptions() {
        return Result.ok(interactionService.mySubscriptions(SecurityUtils.currentUserId()));
    }

    // ---------------- 关注 ----------------

    @PostMapping("/follows/{userId}")
    @OperationLog(action = "FOLLOW_ADD", objectType = "USER", objectIdParam = "{userId}")
    public Result<Void> follow(@PathVariable Long userId) {
        interactionService.follow(SecurityUtils.currentUserId(), userId);
        return Result.ok();
    }

    @DeleteMapping("/follows/{userId}")
    @OperationLog(action = "FOLLOW_REMOVE", objectType = "USER", objectIdParam = "{userId}")
    public Result<Void> unfollow(@PathVariable Long userId) {
        interactionService.unfollow(SecurityUtils.currentUserId(), userId);
        return Result.ok();
    }

    @GetMapping("/follows/my")
    public Result<List<Map<String, Object>>> myFollowing() {
        return Result.ok(interactionService.myFollowing(SecurityUtils.currentUserId()));
    }

    /** 已出版文章下载（匿名可下，计入下载量） */
    @GetMapping("/published/{id}/file")
    public ResponseEntity<Resource> downloadPublished(@PathVariable Long id,
                                                      @RequestParam(required = false) Long versionId) throws IOException {
        com.journalflow.entity.Manuscript manuscript = manuscriptMapper.selectById(id);
        if (manuscript == null || !"PUBLISHED".equals(manuscript.getStatus())) {
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.NOT_FOUND, "文章不存在或未发布");
        }
        Long currentVersionId = manuscript == null ? null : manuscript.getCurrentVersionId();
        if (currentVersionId == null || (versionId != null && !currentVersionId.equals(versionId))) {
            // 公开下载只允许出版时的当前版本，历史投稿/修回版本不对匿名读者开放。
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.NOT_FOUND, "版本不存在");
        }
        com.journalflow.entity.ManuscriptVersion v = versionMapper.selectById(currentVersionId);
        if (v == null || !id.equals(v.getManuscriptId())) {
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.NOT_FOUND, "文件不存在");
        }
        Path path = fileStorage.resolve(v.getFilePath());
        if (!java.nio.file.Files.isRegularFile(path)) {
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.NOT_FOUND, "文件不存在");
        }
        viewStatMapper.incrementDownload(id, 1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(v.getFileName(), StandardCharsets.UTF_8))
                .contentLength(java.nio.file.Files.size(path))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }
}
