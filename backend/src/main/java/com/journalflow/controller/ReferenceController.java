package com.journalflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.common.Result;
import com.journalflow.entity.Category;
import com.journalflow.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceController {
    private final CategoryMapper categoryMapper;
    private final com.journalflow.mapper.TagMapper tagMapper;

    @GetMapping("/tags")
    public Result<List<com.journalflow.entity.Tag>> tags() {
        return Result.ok(tagMapper.selectList(new LambdaQueryWrapper<com.journalflow.entity.Tag>()
                .orderByAsc(com.journalflow.entity.Tag::getName)));
    }

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getParentId).orderByAsc(Category::getSortNo)));
    }
}
