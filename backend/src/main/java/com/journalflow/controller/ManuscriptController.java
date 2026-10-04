package com.journalflow.controller;

import com.journalflow.common.OperationLog;
import com.journalflow.common.PageVO;
import com.journalflow.common.Pagination;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.ManuscriptDtos.SaveRequest;
import com.journalflow.dto.ManuscriptDtos.CopyrightRequest;
import com.journalflow.entity.CopyrightRecord;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.ManuscriptVersion;
import com.journalflow.security.UserDetailsImpl;
import com.journalflow.service.ManuscriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manuscripts")
@RequiredArgsConstructor
public class ManuscriptController {
    private final ManuscriptService manuscriptService;
    private final com.journalflow.service.FileStorageService fileStorage;
    private final com.journalflow.service.EditorFlowService editorFlowService;

    // ---------------- 作者侧 ----------------

    @PostMapping
    @PreAuthorize("hasRole('AUTHOR')")
    @OperationLog(action = "CREATE_MANUSCRIPT", objectType = "MANUSCRIPT")
    public Result<Manuscript> create(@Valid @RequestBody SaveRequest req) {
        return Result.ok(manuscriptService.createDraft(SecurityUtils.currentUserId(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('AUTHOR')")
    @OperationLog(action = "UPDATE_MANUSCRIPT", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SaveRequest req) {
        manuscriptService.updateDraft(id, SecurityUtils.currentUserId(), req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('AUTHOR')")
    @OperationLog(action = "DELETE_MANUSCRIPT", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> delete(@PathVariable Long id) {
        manuscriptService.deleteDraft(id, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @PostMapping(value = "/{id}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AUTHOR')")
    @OperationLog(action = "SUBMIT_MANUSCRIPT", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<ManuscriptVersion> submit(@PathVariable Long id,
                                            @RequestPart("file") MultipartFile file) {
        return Result.ok(manuscriptService.submit(id, SecurityUtils.currentUserId(), file));
    }

    @PostMapping(value = "/{id}/revisions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AUTHOR')")
    @OperationLog(action = "REVISE_MANUSCRIPT", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<ManuscriptVersion> revise(@PathVariable Long id,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(value = "changeNote", required = false) String changeNote,
                                            @RequestParam(value = "responseToReviewers", required = false) String responseToReviewers) {
        return Result.ok(manuscriptService.revise(id, SecurityUtils.currentUserId(), file, changeNote, responseToReviewers));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('AUTHOR')")
    @OperationLog(action = "WITHDRAW_MANUSCRIPT", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> withdraw(@PathVariable Long id) {
        manuscriptService.withdraw(id, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/copyright")
    @PreAuthorize("hasRole('AUTHOR')")
    @OperationLog(action = "SIGN_COPYRIGHT", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<CopyrightRecord> signCopyright(@PathVariable Long id,
                                                  @Valid @RequestBody CopyrightRequest req) {
        return Result.ok(editorFlowService.signCopyright(id, SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('AUTHOR')")
    public Result<PageVO<Map<String, Object>>> my(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword) {
        Pagination.validate(page, size);
        return Result.ok(manuscriptService.pageMy(SecurityUtils.currentUserId(), page, size, status, keyword));
    }

    // ---------------- 通用 ----------------

    @GetMapping
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public Result<PageVO<Map<String, Object>>> all(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long categoryId) {
        Pagination.validate(page, size);
        return Result.ok(manuscriptService.pageAll(page, size, status, keyword, categoryId));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id,
                                              @AuthenticationPrincipal UserDetails user) {
        return Result.ok(manuscriptService.detail(id, (UserDetailsImpl) user));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ManuscriptVersion>> versions(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserDetails user) {
        @SuppressWarnings("unchecked")
        List<ManuscriptVersion> versions = (List<ManuscriptVersion>)
                manuscriptService.detail(id, (UserDetailsImpl) user).get("versions");
        return Result.ok(versions);
    }

    @GetMapping("/{id}/timeline")
    public Result<List<Map<String, Object>>> timeline(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserDetails user) {
        return Result.ok(manuscriptService.timeline(id, (UserDetailsImpl) user));
    }

    /** 稿件文件下载（作者/编辑/被分配审稿人/管理员） */
    @GetMapping("/{id}/files/{versionId}")
    public ResponseEntity<Resource> file(@PathVariable Long id, @PathVariable Long versionId,
                                         @AuthenticationPrincipal UserDetails user) throws IOException {
        Map<String, Object> detail = manuscriptService.detail(id, (UserDetailsImpl) user);
        @SuppressWarnings("unchecked")
        List<ManuscriptVersion> versions = (List<ManuscriptVersion>) detail.get("versions");
        ManuscriptVersion target = versions.stream()
                .filter(v -> versionId.equals(v.getId()))
                .findFirst()
                .orElseThrow(() -> new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.NOT_FOUND, "版本不存在"));
        Path path = fileStorage.resolve(target.getFilePath());
        if (!java.nio.file.Files.isRegularFile(path)) {
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.NOT_FOUND, "文件不存在");
        }
        String fileName = target.getFileName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                .contentLength(java.nio.file.Files.size(path))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }
}
