package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.journalflow.common.BizException;
import com.journalflow.common.PageVO;
import com.journalflow.dto.AdminDtos.UserCreateRequest;
import com.journalflow.entity.Category;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.OperationLog;
import com.journalflow.entity.User;
import com.journalflow.entity.UserRole;
import com.journalflow.mapper.CategoryMapper;
import com.journalflow.mapper.ManuscriptMapper;
import com.journalflow.mapper.OperationLogMapper;
import com.journalflow.mapper.UserMapper;
import com.journalflow.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 管理端：用户管理、学科分类管理、操作日志审计。
 */
@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final CategoryMapper categoryMapper;
    private final ManuscriptMapper manuscriptMapper;
    private final OperationLogMapper operationLogMapper;
    private final com.journalflow.mapper.RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    // ---------------- 用户管理 ----------------

    public PageVO<Map<String, Object>> pageUsers(long page, long size, String keyword, String roleCode, Integer status) {
        Page<User> p = new Page<>(page, size);
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>()
                .eq(status != null, User::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(User::getUsername, keyword).or().like(User::getRealName, keyword))
                .orderByAsc(User::getId);
        Page<User> result = userMapper.selectPage(p, w);
        List<Map<String, Object>> rows = result.getRecords().stream().map(u -> {
            var roles = userMapper.selectRoleCodes(u.getId());
            return Map.<String, Object>of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "realName", u.getRealName(),
                    "email", u.getEmail() == null ? "" : u.getEmail(),
                    "affiliation", u.getAffiliation() == null ? "" : u.getAffiliation(),
                    "researchFields", u.getResearchFields() == null ? "" : u.getResearchFields(),
                    "status", u.getStatus(),
                    "roles", roles,
                    "createdAt", u.getCreatedAt());
        })
        .filter(row -> roleCode == null || roleCode.isBlank() || ((List<?>) row.get("roles")).contains(roleCode))
        .toList();
        return PageVO.of(rows, result.getTotal(), page, size);
    }

    @Transactional
    public void updateStatus(Long id, Integer status, Long operatorId) {
        User u = userMapper.selectById(id);
        if (u == null) throw BizException.notFound("用户不存在");
        if (u.getId().equals(operatorId)) {
            throw BizException.badParam("不能禁用自己的账号");
        }
        u.setStatus(status);
        userMapper.updateById(u);
    }

    @Transactional
    public void updateRoles(Long id, List<String> roleCodes) {
        User u = userMapper.selectById(id);
        if (u == null) throw BizException.notFound("用户不存在");
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        for (String code : roleCodes) {
            authService.grantRole(id, code);
        }
    }

    @Transactional
    public void createUser(UserCreateRequest req) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exists > 0) {
            throw BizException.badParam("用户名已被占用");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRealName(req.getRealName());
        u.setEmail(req.getEmail());
        u.setAffiliation(req.getAffiliation());
        u.setStatus(1);
        userMapper.insert(u);
        if (req.getRoleCodes() != null && !req.getRoleCodes().isBlank()) {
            for (String code : req.getRoleCodes().split(",")) {
                authService.grantRole(u.getId(), code.trim());
            }
        } else {
            authService.grantRole(u.getId(), "AUTHOR");
        }
    }

    // ---------------- 学科分类 ----------------

    public List<Category> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getParentId).orderByAsc(Category::getSortNo));
    }

    @Transactional
    public Category createCategory(String name, Long parentId, Integer sortNo) {
        validateCategoryParent(null, parentId);
        Category c = new Category();
        c.setName(name);
        c.setParentId(parentId);
        c.setSortNo(sortNo == null ? 0 : sortNo);
        categoryMapper.insert(c);
        return c;
    }

    @Transactional
    public void updateCategory(Long id, String name, Long parentId, Integer sortNo) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw BizException.notFound("分类不存在");
        validateCategoryParent(id, parentId);
        if (name != null) c.setName(name);
        c.setParentId(parentId);
        if (sortNo != null) c.setSortNo(sortNo);
        categoryMapper.updateById(c);
    }

    private void validateCategoryParent(Long categoryId, Long parentId) {
        Long cursor = parentId;
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(categoryId)) throw BizException.badParam("分类父子关系不能形成循环");
            Category parent = categoryMapper.selectById(cursor);
            if (parent == null) throw BizException.badParam("父分类不存在");
            cursor = parent.getParentId();
            if (++depth > 100) throw BizException.badParam("分类层级异常");
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (children > 0) {
            throw BizException.conflict("存在子分类，无法删除");
        }
        Long used = manuscriptMapper.selectCount(new LambdaQueryWrapper<Manuscript>()
                .eq(Manuscript::getCategoryId, id));
        if (used > 0) {
            throw BizException.conflict("已有稿件使用该分类，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    // ---------------- 操作日志 ----------------

    public PageVO<Map<String, Object>> pageLogs(long page, long size, Long userId, String action,
                                                Integer success, String dateFrom, String dateTo) {
        Page<Map<String, Object>> p = new Page<>(page, size);
        List<Map<String, Object>> rows = operationLogMapper.selectPage(p, userId, action, success, dateFrom, dateTo);
        return PageVO.of(rows, p.getTotal(), page, size);
    }
}
