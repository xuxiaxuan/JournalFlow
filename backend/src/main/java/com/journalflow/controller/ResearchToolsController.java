package com.journalflow.controller;

import com.journalflow.common.*;
import com.journalflow.security.UserDetailsImpl;
import com.journalflow.service.ResearchToolsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ResearchToolsController {
    private final ResearchToolsService tools;

    @GetMapping("/manuscripts/{id}/compare")
    public Result<?> compare(@PathVariable Long id, @RequestParam Long from, @RequestParam Long to, @AuthenticationPrincipal UserDetailsImpl user) {
        return Result.ok(tools.compare(id, from, to, user));
    }
    @GetMapping("/llm/tasks/{id}/evidence")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public Result<?> evidence(@PathVariable Long id, @AuthenticationPrincipal UserDetailsImpl user) { return Result.ok(tools.evidence(id, user)); }
    @GetMapping("/manuscripts/{id}/responses")
    public Result<?> checklist(@PathVariable Long id, @AuthenticationPrincipal UserDetailsImpl user) { return Result.ok(tools.checklist(id, user)); }
    @Data
    public static class ReplyRequest {
        @NotNull private Long reportId;
        @NotNull @Min(0) private Integer issueIndex;
        @NotNull @Size(max=5000) private String responseText;
        @NotNull @Size(max=1000) private String changeLocation = "";
        private Long versionId;
        private Long baseId;
    }
    @PostMapping("/manuscripts/{id}/responses")
    @OperationLog(action="SAVE_REVIEW_RESPONSE", objectType="MANUSCRIPT", objectIdParam="{id}")
    public Result<?> save(@PathVariable Long id, @Valid @RequestBody ReplyRequest req, @AuthenticationPrincipal UserDetailsImpl user) {
        return Result.ok(tools.save(id, req.reportId, req.issueIndex, req.responseText, req.changeLocation, req.versionId, req.baseId, user));
    }
    @PostMapping("/manuscripts/{id}/responses/{responseId}/confirm")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    @OperationLog(action="CONFIRM_REVIEW_RESPONSE", objectType="MANUSCRIPT", objectIdParam="{id}")
    public Result<?> confirm(@PathVariable Long id, @PathVariable Long responseId, @AuthenticationPrincipal UserDetailsImpl user) { return Result.ok(tools.confirm(id, responseId, user)); }
}
