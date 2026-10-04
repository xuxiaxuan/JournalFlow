package com.journalflow.controller;

import com.journalflow.common.PageVO;
import com.journalflow.common.Pagination;
import com.journalflow.common.Result;
import com.journalflow.common.OperationLog;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.LlmDtos.ConfirmRequest;
import com.journalflow.dto.LlmDtos.CreateTaskRequest;
import com.journalflow.entity.LlmTask;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.EditorialDecision;
import com.journalflow.mapper.EditorialDecisionMapper;
import com.journalflow.mapper.ManuscriptMapper;
import com.journalflow.service.LlmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {
    private final LlmService llmService;
    private final ManuscriptMapper manuscriptMapper;
    private final EditorialDecisionMapper decisionMapper;

    /** 发起 AI 任务：EXTRACT/SUMMARY 仅编辑；EXPLAIN 作者本人或编辑 */
    @PostMapping("/tasks")
    @com.journalflow.common.OperationLog(action = "LLM_TASK_CREATE", objectType = "MANUSCRIPT")
    public Result<LlmTask> create(@Valid @RequestBody CreateTaskRequest req) {
        String scene = req.getScene();
        Long uid = SecurityUtils.currentUserId();
        authorizeScene(scene, req.getManuscriptId(), uid);
        return Result.ok(llmService.createTask(scene, req.getManuscriptId(), uid));
    }

    private void authorizeScene(String scene, Long manuscriptId, Long uid) {
        if ("EXTRACT".equals(scene) || "SUMMARY".equals(scene)) {
            checkRole("EDITOR");
        } else if ("EXPLAIN".equals(scene)) {
            Manuscript m = manuscriptMapper.selectById(manuscriptId);
            boolean editor = SecurityUtils.hasRole("EDITOR") || SecurityUtils.hasRole("ADMIN");
            if (!editor && (m == null || !uid.equals(m.getSubmitterId()))) {
                throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.FORBIDDEN,
                        "仅稿件作者或编辑可发起解释任务");
            }
            if (!editor && decisionMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EditorialDecision>()
                            .eq(EditorialDecision::getManuscriptId, manuscriptId)
                            .eq(EditorialDecision::getStage, "REVIEW")) == 0) {
                throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.CONFLICT,
                        "编辑尚未公开审稿决定，暂不能生成意见解释");
            }
        } else {
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.BAD_PARAM,
                    "scene 必须是 EXTRACT/SUMMARY/EXPLAIN");
        }
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('AUTHOR','EDITOR','ADMIN')")
    public Result<PageVO<Map<String, Object>>> tasks(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String scene,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long manuscriptId) {
        Pagination.validate(page, size);
        boolean editor = SecurityUtils.hasRole("EDITOR") || SecurityUtils.hasRole("ADMIN");
        String effectiveScene = editor ? scene : "EXPLAIN";
        Long createdBy = editor ? null : SecurityUtils.currentUserId();
        return Result.ok(llmService.pageTasks(page, size, effectiveScene, status, manuscriptId, createdBy));
    }

    @GetMapping("/tasks/{id}")
    public Result<LlmTask> get(@PathVariable Long id) {
        LlmTask task = llmService.get(id);
        boolean editor = SecurityUtils.hasRole("EDITOR") || SecurityUtils.hasRole("ADMIN");
        if (!editor) {
            boolean ownExplanation = "EXPLAIN".equals(task.getScene())
                    && SecurityUtils.currentUserId().equals(task.getCreatedBy());
            if (!ownExplanation) {
                throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.FORBIDDEN, "无权查看该任务");
            }
        }
        return Result.ok(task);
    }

    /** 人工确认：仅编辑；EXTRACT 场景确认后自动入库更新稿件元数据 */
    @PostMapping("/tasks/{id}/confirm")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    @com.journalflow.common.OperationLog(action = "LLM_TASK_CONFIRM", objectType = "LLM_TASK", objectIdParam = "{id}")
    public Result<LlmTask> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmRequest req) {
        return Result.ok(llmService.confirm(id, SecurityUtils.currentUserId(), req.getConfirmedOutput()));
    }

    @PostMapping("/tasks/{id}/retry")
    @com.journalflow.common.OperationLog(action = "LLM_TASK_RETRY", objectType = "LLM_TASK", objectIdParam = "{id}")
    public Result<LlmTask> retry(@PathVariable Long id) {
        LlmTask failed = llmService.get(id);
        Long uid = SecurityUtils.currentUserId();
        authorizeScene(failed.getScene(), failed.getManuscriptId(), uid);
        if (!SecurityUtils.hasRole("EDITOR") && !SecurityUtils.hasRole("ADMIN")
                && !uid.equals(failed.getCreatedBy())) {
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.FORBIDDEN,
                    "只能重试本人创建的解释任务");
        }
        return Result.ok(llmService.retry(id, uid));
    }

    private void checkRole(String role) {
        if (!SecurityUtils.hasRole(role) && !SecurityUtils.hasRole("ADMIN")) {
            throw new com.journalflow.common.BizException(com.journalflow.common.ErrorCode.FORBIDDEN,
                    "仅编辑可执行该操作");
        }
    }

}
