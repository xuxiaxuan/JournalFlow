package com.journalflow.controller;

import com.journalflow.common.PageVO;
import com.journalflow.common.Pagination;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.service.PublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/published")
@RequiredArgsConstructor
public class PublicController {
    private final PublicService publicService;

    /** 公开检索：多条件组合 + 分页 + 热度/时间排序 */
    @GetMapping
    public Result<PageVO<Map<String, Object>>> list(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long categoryId,
                                                    @RequestParam(required = false) String dateFrom,
                                                    @RequestParam(required = false) String dateTo,
                                                    @RequestParam(defaultValue = "new") String sortBy) {
        Pagination.validate(page, size);
        return Result.ok(publicService.publishedPage(page, size, keyword, categoryId, dateFrom, dateTo, sortBy));
    }

    @GetMapping("/recommend")
    public Result<List<Map<String, Object>>> recommend(@RequestParam(defaultValue = "6") int limit) {
        return Result.ok(publicService.recommend(SecurityUtils.currentUserId(), limit));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(publicService.detail(id, SecurityUtils.currentUserId()));
    }
}
