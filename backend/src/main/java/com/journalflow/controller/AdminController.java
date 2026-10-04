package com.journalflow.controller;

import com.journalflow.common.BizException;
import com.journalflow.common.OperationLog;
import com.journalflow.common.PageVO;
import com.journalflow.common.Pagination;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.AdminDtos.CategoryRequest;
import com.journalflow.dto.AdminDtos.UserCreateRequest;
import com.journalflow.dto.AdminDtos.UserRolesRequest;
import com.journalflow.dto.AdminDtos.UserStatusRequest;
import com.journalflow.entity.Category;
import com.journalflow.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/users")
    public Result<PageVO<Map<String, Object>>> users(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String roleCode,
                                                     @RequestParam(required = false) Integer status) {
        Pagination.validate(page, size);
        return Result.ok(adminService.pageUsers(page, size, keyword, roleCode, status));
    }

    @PostMapping("/users")
    @OperationLog(action = "ADMIN_CREATE_USER", objectType = "USER")
    public Result<Void> createUser(@Valid @RequestBody UserCreateRequest req) {
        adminService.createUser(req);
        return Result.ok();
    }

    @PutMapping("/users/{id}/status")
    @OperationLog(action = "ADMIN_USER_STATUS", objectType = "USER", objectIdParam = "{id}")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest req) {
        int status;
        try {
            status = Integer.parseInt(req.getStatus());
        } catch (NumberFormatException e) {
            throw BizException.badParam("状态必须是 0 或 1");
        }
        if (status != 0 && status != 1) throw BizException.badParam("状态必须是 0 或 1");
        adminService.updateStatus(id, status, SecurityUtils.currentUserId());
        return Result.ok();
    }

    @PutMapping("/users/{id}/roles")
    @OperationLog(action = "ADMIN_USER_ROLES", objectType = "USER", objectIdParam = "{id}")
    public Result<Void> updateRoles(@PathVariable Long id, @Valid @RequestBody UserRolesRequest req) {
        List<String> roles = Arrays.stream(req.getRoleCodes().split(","))
                .map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
        if (roles.isEmpty()) throw BizException.badParam("至少选择一个角色");
        adminService.updateRoles(id, roles);
        return Result.ok();
    }

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(adminService.categories());
    }

    @PostMapping("/categories")
    @OperationLog(action = "ADMIN_CATEGORY_CREATE", objectType = "CATEGORY")
    public Result<Category> createCategory(@Valid @RequestBody CategoryRequest req) {
        return Result.ok(adminService.createCategory(req.getName(), req.getParentId(), req.getSortNo()));
    }

    @PutMapping("/categories/{id}")
    @OperationLog(action = "ADMIN_CATEGORY_UPDATE", objectType = "CATEGORY", objectIdParam = "{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        adminService.updateCategory(id, req.getName(), req.getParentId(), req.getSortNo());
        return Result.ok();
    }

    @DeleteMapping("/categories/{id}")
    @OperationLog(action = "ADMIN_CATEGORY_DELETE", objectType = "CATEGORY", objectIdParam = "{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return Result.ok();
    }

    @GetMapping("/logs")
    public Result<PageVO<Map<String, Object>>> logs(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) Integer success,
                                                    @RequestParam(required = false) String dateFrom,
                                                    @RequestParam(required = false) String dateTo) {
        Pagination.validate(page, size);
        return Result.ok(adminService.pageLogs(page, size, userId, action, success, dateFrom, dateTo));
    }
}
