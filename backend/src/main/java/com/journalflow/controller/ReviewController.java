package com.journalflow.controller;

import com.journalflow.common.OperationLog;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.ReviewDtos.DeclineRequest;
import com.journalflow.dto.ReviewDtos.ReportRequest;
import com.journalflow.entity.ReviewReport;
import com.journalflow.service.ReviewFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@PreAuthorize("hasRole('REVIEWER')")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewFlowService reviewFlowService;

    @GetMapping("/my-assignments")
    public Result<List<Map<String, Object>>> myAssignments(@RequestParam(required = false) String status) {
        return Result.ok(reviewFlowService.myAssignments(SecurityUtils.currentUserId(), status));
    }

    @GetMapping("/assignments/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(reviewFlowService.assignmentDetail(id, SecurityUtils.currentUserId()));
    }

    @PostMapping("/assignments/{id}/accept")
    @OperationLog(action = "ACCEPT_REVIEW", objectType = "REVIEW_ASSIGNMENT", objectIdParam = "{id}")
    public Result<Void> accept(@PathVariable Long id) {
        reviewFlowService.accept(id, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @PostMapping("/assignments/{id}/decline")
    @OperationLog(action = "DECLINE_REVIEW", objectType = "REVIEW_ASSIGNMENT", objectIdParam = "{id}")
    public Result<Void> decline(@PathVariable Long id, @RequestBody(required = false) DeclineRequest req) {
        reviewFlowService.decline(id, SecurityUtils.currentUserId(), req == null ? new DeclineRequest() : req);
        return Result.ok();
    }

    @PostMapping("/assignments/{id}/report")
    @OperationLog(action = "SUBMIT_REPORT", objectType = "REVIEW_ASSIGNMENT", objectIdParam = "{id}")
    public Result<ReviewReport> submitReport(@PathVariable Long id, @Valid @RequestBody ReportRequest req) {
        return Result.ok(reviewFlowService.submitReport(id, SecurityUtils.currentUserId(), req));
    }
}
