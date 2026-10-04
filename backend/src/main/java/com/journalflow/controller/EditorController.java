package com.journalflow.controller;

import com.journalflow.common.OperationLog;
import com.journalflow.common.PageVO;
import com.journalflow.common.Pagination;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.EditorDtos.DecisionRequest;
import com.journalflow.dto.EditorDtos.FormatReviewRequest;
import com.journalflow.dto.ManuscriptDtos.PublishRequest;
import com.journalflow.dto.ReviewDtos.AssignReviewersRequest;
import com.journalflow.entity.CopyrightRecord;
import com.journalflow.entity.ReviewAssignment;
import com.journalflow.entity.User;
import com.journalflow.service.EditorFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/editor")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
@RequiredArgsConstructor
public class EditorController {
    private final EditorFlowService editorFlowService;
    private final com.journalflow.mapper.CopyrightRecordMapper copyrightRecordMapper;
    private final com.journalflow.mapper.UserMapper userMapper;

    @GetMapping("/reviewers")
    public Result<List<Map<String, Object>>> reviewers() {
        List<Map<String, Object>> rows = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getStatus, 1)
                        .inSql(User::getId, "SELECT user_id FROM user_role ur JOIN role r ON r.id = ur.role_id WHERE r.code = 'REVIEWER'")
                        .orderByAsc(User::getRealName))
                .stream().map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "realName", u.getRealName(),
                        "affiliation", u.getAffiliation() == null ? "" : u.getAffiliation(),
                        "researchFields", u.getResearchFields() == null ? "" : u.getResearchFields()))
                .toList();
        return Result.ok(rows);
    }

    @GetMapping("/format-review-queue")
    public Result<PageVO<Map<String, Object>>> queue(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String keyword) {
        Pagination.validate(page, size);
        return Result.ok(editorFlowService.formatQueue(page, size, status, keyword));
    }

    @PostMapping("/manuscripts/{id}/format-review/start")
    @OperationLog(action = "FORMAT_REVIEW_START", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> startFormatReview(@PathVariable Long id) {
        editorFlowService.startFormatReview(id, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @PostMapping("/manuscripts/{id}/format-review")
    @OperationLog(action = "FORMAT_REVIEW", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> completeFormatReview(@PathVariable Long id, @Valid @RequestBody FormatReviewRequest req) {
        editorFlowService.completeFormatReview(id, SecurityUtils.currentUserId(), req);
        return Result.ok();
    }

    @PostMapping("/manuscripts/{id}/assign-reviewers")
    @OperationLog(action = "ASSIGN_REVIEWERS", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<List<ReviewAssignment>> assignReviewers(@PathVariable Long id,
                                                          @Valid @RequestBody AssignReviewersRequest req) {
        return Result.ok(editorFlowService.assignReviewers(id, SecurityUtils.currentUserId(), req));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @OperationLog(action = "CANCEL_ASSIGNMENT", objectType = "REVIEW_ASSIGNMENT", objectIdParam = "{assignmentId}")
    public Result<Void> cancelAssignment(@PathVariable Long assignmentId) {
        editorFlowService.cancelAssignment(assignmentId, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @PostMapping("/manuscripts/{id}/decision")
    @OperationLog(action = "DECISION", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest req) {
        editorFlowService.decide(id, SecurityUtils.currentUserId(), req);
        return Result.ok();
    }

    @PostMapping("/manuscripts/{id}/publish")
    @OperationLog(action = "PUBLISH", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> publish(@PathVariable Long id, @Valid @RequestBody PublishRequest req) {
        editorFlowService.publish(id, SecurityUtils.currentUserId(), req);
        return Result.ok();
    }

    @PostMapping("/manuscripts/{id}/archive")
    @OperationLog(action = "ARCHIVE", objectType = "MANUSCRIPT", objectIdParam = "{id}")
    public Result<Void> archive(@PathVariable Long id) {
        editorFlowService.archive(id, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @GetMapping("/copyright/{manuscriptId}")
    public Result<List<CopyrightRecord>> copyrightList(@PathVariable Long manuscriptId) {
        return Result.ok(copyrightRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CopyrightRecord>()
                        .eq(CopyrightRecord::getManuscriptId, manuscriptId)
                        .orderByDesc(CopyrightRecord::getSignedAt)));
    }

}
