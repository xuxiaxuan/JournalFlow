package com.journalflow.controller;

import com.journalflow.common.Result;
import com.journalflow.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    @GetMapping("/submission-trend")
    public Result<List<Map<String, Object>>> submissionTrend(@RequestParam(defaultValue = "12") int months) {
        return Result.ok(statsService.submissionTrend(bounded(months, 1, 36)));
    }

    @GetMapping("/review-cycle")
    public Result<List<Map<String, Object>>> reviewCycle(@RequestParam(defaultValue = "12") int months) {
        return Result.ok(statsService.reviewCycle(bounded(months, 1, 36)));
    }

    @GetMapping("/report-handling")
    public Result<List<Map<String, Object>>> reportHandling() {
        return Result.ok(statsService.reportHandling());
    }

    @GetMapping("/top-authors")
    public Result<List<Map<String, Object>>> topAuthors(@RequestParam(defaultValue = "10") int top) {
        return Result.ok(statsService.topAuthors(bounded(top, 1, 50)));
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
