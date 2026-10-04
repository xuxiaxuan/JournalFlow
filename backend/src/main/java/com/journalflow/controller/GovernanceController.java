package com.journalflow.controller;

import com.journalflow.common.OperationLog;
import com.journalflow.common.PageVO;
import com.journalflow.common.Pagination;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.EditorDtos.AppealCreateRequest;
import com.journalflow.dto.EditorDtos.AppealHandleRequest;
import com.journalflow.dto.EditorDtos.ReportCreateRequest;
import com.journalflow.dto.EditorDtos.ReportHandleRequest;
import com.journalflow.entity.Appeal;
import com.journalflow.entity.Report;
import com.journalflow.service.AppealService;
import com.journalflow.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GovernanceController {
    private final ReportService reportService;
    private final AppealService appealService;

    @PostMapping("/reports")
    @OperationLog(action = "REPORT_CREATE", objectType = "REPORT")
    public Result<Report> createReport(@Valid @RequestBody ReportCreateRequest req) {
        return Result.ok(reportService.create(SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/reports")
    public Result<PageVO<Map<String, Object>>> reports(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String targetType) {
        Pagination.validate(page, size);
        boolean staff = SecurityUtils.hasRole("EDITOR") || SecurityUtils.hasRole("ADMIN");
        Long reporterId = staff ? null : SecurityUtils.currentUserId();
        return Result.ok(reportService.page(reporterId, status, targetType, page, size));
    }

    @PostMapping("/reports/{id}/handle")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    @OperationLog(action = "REPORT_HANDLE", objectType = "REPORT", objectIdParam = "{id}")
    public Result<Void> handleReport(@PathVariable Long id, @Valid @RequestBody ReportHandleRequest req) {
        reportService.handle(id, SecurityUtils.currentUserId(), SecurityUtils.hasRole("ADMIN"), req);
        return Result.ok();
    }

    @PostMapping("/appeals")
    @OperationLog(action = "APPEAL_CREATE", objectType = "APPEAL")
    public Result<Appeal> createAppeal(@Valid @RequestBody AppealCreateRequest req) {
        return Result.ok(appealService.create(SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/appeals")
    public Result<PageVO<Map<String, Object>>> appeals(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @RequestParam(required = false) String status) {
        Pagination.validate(page, size);
        Long appellantId = SecurityUtils.hasRole("ADMIN") ? null : SecurityUtils.currentUserId();
        return Result.ok(appealService.page(appellantId, status, page, size));
    }

    @PostMapping("/appeals/{id}/handle")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "APPEAL_HANDLE", objectType = "APPEAL", objectIdParam = "{id}")
    public Result<Void> handleAppeal(@PathVariable Long id, @Valid @RequestBody AppealHandleRequest req) {
        appealService.handle(id, SecurityUtils.currentUserId(), req);
        return Result.ok();
    }
}
