package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.common.PageVO;
import com.journalflow.entity.*;
import com.journalflow.llm.LlmProvider;
import com.journalflow.llm.LlmProviderRouter;
import com.journalflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型智能化模块（人在回路）：
 * 场景 A 抽取 TPL-EXTRACT-01 / 场景 B 汇总 TPL-SUMMARY-01 / 场景 C 解释 TPL-EXPLAIN-01。
 * 全部调用写 llm_task 留痕；输出 JSON 强 Schema 校验（失败重试 1 次）；
 * 结果必须经人工确认（CONFIRMED）才对业务生效，raw_output 永不覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {
    public static final String TPL_EXTRACT = "TPL-EXTRACT-01";
    public static final String TPL_SUMMARY = "TPL-SUMMARY-01";
    public static final String TPL_EXPLAIN = "TPL-EXPLAIN-01";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmTaskMapper llmTaskMapper;
    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptVersionMapper versionMapper;
    private final ReviewReportMapper reportMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final MetadataRevisionMapper metadataRevisionMapper;
    private final LlmProviderRouter providerRouter;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ManuscriptTextExtractor textExtractor;

    // ---------------- 发起任务 ----------------

    @Transactional
    public LlmTask createTask(String scene, Long manuscriptId, Long userId) {
        Manuscript m = manuscriptMapper.selectById(manuscriptId);
        if (m == null) throw BizException.notFound("稿件不存在");
        if (m.getCurrentVersionId() == null) {
            throw BizException.badParam("稿件还没有提交过版本，无法发起 AI 任务");
        }
        ManuscriptVersion version = versionMapper.selectById(m.getCurrentVersionId());
        Long active = llmTaskMapper.selectCount(new LambdaQueryWrapper<LlmTask>()
                .eq(LlmTask::getScene, scene)
                .eq(LlmTask::getVersionId, version.getId())
                .in(LlmTask::getStatus, List.of("RUNNING", "SUCCESS")));
        if (active > 0) {
            throw BizException.conflict("当前版本已有运行中或待确认的同类 AI 任务");
        }

        String templateId;
        String systemPrompt;
        String userPrompt;
        switch (scene) {
            case "EXTRACT" -> {
                templateId = TPL_EXTRACT;
                List<String> categoryNames = categoryMapper.selectList(null).stream().map(Category::getName).toList();
                systemPrompt = "你是学术期刊编辑部助手，从稿件片段抽取元数据，只输出 JSON，不得编造未出现的信息。"
                        + "输出字段：{title, abstract(不超过400字), keywords(3到8个), primary_category(必须取自以下枚举："
                        + String.join("/", categoryNames) + "), language(zh或en), word_count_est, reference_count_est, "
                        + "has_ethics_statement, possible_issues[]}。只输出 JSON 本体，不要任何解释。";
                userPrompt = buildExtractInput(version);
            }
            case "SUMMARY" -> {
                templateId = TPL_SUMMARY;
                List<ReviewReport> reports = reportMapper.selectList(new LambdaQueryWrapper<ReviewReport>()
                        .eq(ReviewReport::getManuscriptId, manuscriptId)
                        .eq(ReviewReport::getVersionId, m.getCurrentVersionId()));
                if (reports.isEmpty()) {
                    throw BizException.badParam("当前版本暂无审稿意见，无法汇总");
                }
                systemPrompt = "你是学术期刊编辑部的审稿意见汇总助手。只归纳，不添加原文没有的信息。"
                        + "输出字段：{summary, major_issues[], minor_issues[], consensus(accept|minor|major|reject), "
                        + "dissent, suggested_decision(ACCEPT|REVISE|REJECT)}。只输出 JSON 本体。";
                userPrompt = buildSummaryInput(reports);
            }
            case "EXPLAIN" -> {
                templateId = TPL_EXPLAIN;
                List<ReviewReport> reports = reportMapper.selectList(new LambdaQueryWrapper<ReviewReport>()
                        .eq(ReviewReport::getManuscriptId, manuscriptId)
                        .eq(ReviewReport::getVersionId, m.getCurrentVersionId()));
                if (reports.isEmpty()) {
                    throw BizException.badParam("当前版本暂无审稿意见，无法解释");
                }
                systemPrompt = "你是学术期刊的作者服务助手，把专家审稿意见转写为作者能理解的通俗解释与修改建议。"
                        + "只基于意见原文，不添加原文没有的信息。输出字段：{explanations:[{issue, plain, suggestion}], "
                        + "action_items[]}。只输出 JSON 本体。";
                userPrompt = buildExplainInput(reports, version);
            }
            default -> throw BizException.badParam("scene 必须是 EXTRACT/SUMMARY/EXPLAIN");
        }

        LlmTask task = new LlmTask();
        task.setScene(scene);
        task.setManuscriptId(manuscriptId);
        task.setVersionId(version.getId());
        task.setTemplateId(templateId);
        task.setInputDigest(abbreviate(userPrompt.replace("\n", " "), 180));
        task.setStatus("RUNNING");
        LlmProvider provider = providerRouter.current();
        task.setProvider(provider.name());
        task.setModel(provider.model());
        task.setCreatedBy(userId);
        task.setCreatedAt(LocalDateTime.now());
        llmTaskMapper.insert(task);
        eventPublisher.publishEvent(new LlmTaskCreatedEvent(task.getId(), systemPrompt, userPrompt));
        return task;
    }

    public void executeTask(Long taskId, String systemPrompt, String userPrompt) {
        LlmTask task = llmTaskMapper.selectById(taskId);
        if (task == null || !"RUNNING".equals(task.getStatus())) return;
        Manuscript manuscript = manuscriptMapper.selectById(task.getManuscriptId());
        // 调用 + 校验失败重试 1 次（含延迟模拟留痕）
        LlmProvider provider = providerRouter.current();
        String lastError = null;
        JsonNode parsed = null;
        String raw = null;
        int latency = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                LlmProvider.ChatResult result = provider.chat(task.getScene(), systemPrompt, userPrompt);
                raw = result.content();
                latency += result.latencyMs();
                parsed = validateAndParse(task.getScene(), raw);
                if ("EXTRACT".equals(task.getScene())) validateExtractCategory(parsed);
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = abbreviate(e.getMessage(), 480);
                log.warn("LLM 任务 {} 第 {} 次尝试失败: {}", task.getId(), attempt + 1, lastError);
            }
        }
        if (parsed == null) {
            task.setStatus("FAILED");
            task.setLatencyMs(latency);
            task.setErrorMsg(lastError);
            task.setRawOutput(raw);
            llmTaskMapper.updateById(task);
            notificationService.create(task.getCreatedBy(), "LLM", "AI 任务执行失败，已转人工",
                    "稿件《" + (manuscript == null ? "" : manuscript.getTitle()) + "》的 " + ManuscriptService.sceneLabel(task.getScene())
                            + "任务失败：" + abbreviate(lastError, 80) + "。流程不阻塞，可人工处理或稍后重试。", task.getId());
        } else {
            task.setStatus("SUCCESS");
            task.setLatencyMs(latency);
            task.setRawOutput(raw);
            task.setParsedOutput(parsed.toString());
            llmTaskMapper.updateById(task);
            notificationService.create(task.getCreatedBy(), "LLM", "AI 任务执行完成",
                    ManuscriptService.sceneLabel(task.getScene()) + "任务 #" + task.getId() + " 已完成，请查看结果。", task.getId());
        }
    }

    public void markUnexpectedFailure(Long taskId, String message) {
        LlmTask task = llmTaskMapper.selectById(taskId);
        if (task == null || !"RUNNING".equals(task.getStatus())) return;
        task.setStatus("FAILED");
        task.setErrorMsg(abbreviate(message == null ? "后台任务执行异常" : message, 480));
        llmTaskMapper.updateById(task);
        notificationService.create(task.getCreatedBy(), "LLM", "AI 任务执行失败，已转人工",
                "任务 #" + task.getId() + " 后台执行异常，可稍后重试。", task.getId());
    }

    // ---------------- 查询与溯源 ----------------

    public PageVO<Map<String, Object>> pageTasks(long page, long size, String scene, String status,
                                                 Long manuscriptId, Long createdBy) {
        Page<LlmTask> p = new Page<>(page, size);
        LambdaQueryWrapper<LlmTask> w = new LambdaQueryWrapper<LlmTask>()
                .eq(scene != null && !scene.isBlank(), LlmTask::getScene, scene)
                .eq(status != null && !status.isBlank(), LlmTask::getStatus, status)
                .eq(manuscriptId != null, LlmTask::getManuscriptId, manuscriptId)
                .eq(createdBy != null, LlmTask::getCreatedBy, createdBy)
                .orderByDesc(LlmTask::getCreatedAt);
        Page<LlmTask> result = llmTaskMapper.selectPage(p, w);
        List<Map<String, Object>> rows = result.getRecords().stream().map(t -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("scene", t.getScene());
            row.put("sceneLabel", ManuscriptService.sceneLabel(t.getScene()));
            row.put("manuscriptId", t.getManuscriptId());
            Manuscript m = manuscriptMapper.selectById(t.getManuscriptId());
            row.put("manuscriptTitle", m == null ? "" : m.getTitle());
            row.put("templateId", t.getTemplateId());
            row.put("provider", t.getProvider());
            row.put("model", t.getModel());
            row.put("status", t.getStatus());
            row.put("latencyMs", t.getLatencyMs());
            row.put("errorMsg", t.getErrorMsg());
            row.put("createdAt", t.getCreatedAt());
            row.put("confirmedAt", t.getConfirmedAt());
            return row;
        }).toList();
        return PageVO.of(rows, result.getTotal(), page, size);
    }

    public LlmTask get(Long id) {
        LlmTask t = llmTaskMapper.selectById(id);
        if (t == null) throw BizException.notFound("AI 任务不存在");
        return t;
    }

    @Transactional
    public LlmTask retry(Long id, Long userId) {
        LlmTask failed = llmTaskMapper.selectById(id);
        if (failed == null) throw BizException.notFound("AI 任务不存在");
        if (!"FAILED".equals(failed.getStatus())) {
            throw BizException.conflict("仅执行失败的 AI 任务可以重试");
        }
        LlmTask retried = createTask(failed.getScene(), failed.getManuscriptId(), userId);
        retried.setRetryOfTaskId(failed.getId());
        llmTaskMapper.updateById(retried);
        return retried;
    }

    // ---------------- 人工确认（人在回路核心） ----------------

    @Transactional
    public LlmTask confirm(Long id, Long editorId, String confirmedOutput) {
        LlmTask task = llmTaskMapper.selectById(id);
        if (task == null) throw BizException.notFound("AI 任务不存在");
        if (!"SUCCESS".equals(task.getStatus())) {
            throw BizException.conflict("仅 SUCCESS 状态的任务可确认，当前：" + task.getStatus());
        }
        JsonNode confirmed = validateAndParse(task.getScene(), confirmedOutput);
        if ("EXTRACT".equals(task.getScene())) {
            Manuscript current = manuscriptMapper.selectById(task.getManuscriptId());
            if (current == null) throw BizException.notFound("稿件不存在");
            if (!java.util.Objects.equals(current.getCurrentVersionId(), task.getVersionId())) {
                throw BizException.conflict("稿件已更新版本，请为当前版本重新抽取");
            }
            if (List.of("PUBLISHED", "IN_PRODUCTION", "WITHDRAWN", "REJECTED").contains(current.getStatus())) {
                throw BizException.conflict("当前稿件状态不允许写回 AI 元数据");
            }
        }
        if ("EXTRACT".equals(task.getScene())) validateExtractCategory(confirmed);
        task.setConfirmedOutput(confirmed.toString());
        task.setStatus("CONFIRMED");
        task.setConfirmedBy(editorId);
        task.setConfirmedAt(LocalDateTime.now());
        llmTaskMapper.updateById(task);

        if ("EXTRACT".equals(task.getScene())) {
            applyExtract(task, editorId, confirmed);
        }
        return task;
    }

    /** 确认入库：把人工核对后的元数据写回稿件 */
    private void applyExtract(LlmTask task, Long editorId, JsonNode confirmed) {
        Manuscript m = manuscriptMapper.selectById(task.getManuscriptId());
        if (m == null) return;
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("title", m.getTitle());
        before.put("abstract", m.getAbstract_());
        before.put("keywords", m.getKeywords());
        before.put("categoryId", m.getCategoryId());
        JsonNode title = confirmed.get("title");
        JsonNode abstractNode = confirmed.get("abstract");
        JsonNode keywords = confirmed.get("keywords");
        JsonNode category = confirmed.get("primary_category");
        if (title != null && !title.asText().isBlank()) m.setTitle(abbreviate(title.asText(), 200));
        if (abstractNode != null) m.setAbstract_(abbreviate(abstractNode.asText(), 3000));
        if (keywords != null && keywords.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode k : keywords) {
                if (sb.length() > 0) sb.append(";");
                sb.append(k.asText());
            }
            m.setKeywords(abbreviate(sb.toString(), 500));
        }
        if (category != null) {
            Category c = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getName, category.asText()));
            if (c != null) m.setCategoryId(c.getId());
        }
        manuscriptMapper.updateById(m);

        MetadataRevision revision = new MetadataRevision();
        revision.setManuscriptId(m.getId());
        revision.setVersionId(task.getVersionId());
        revision.setLlmTaskId(task.getId());
        revision.setChangedBy(editorId);
        try {
            revision.setBeforeData(MAPPER.writeValueAsString(before));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL, "元数据修订快照保存失败");
        }
        revision.setAfterData(confirmed.toString());
        revision.setCreatedAt(LocalDateTime.now());
        metadataRevisionMapper.insert(revision);
    }

    // ---------------- 提示词输入构造 ----------------

    public String buildExtractInput(ManuscriptVersion v) {
        String extracted = textExtractor.extract(v);
        String body = extracted == null || extracted.isBlank()
                ? "（文件文本无法自动提取，请以标题、摘要和关键词为准）"
                : extracted;
        return "【标题】" + nvl(v.getTitle())
                + "\n【摘要】" + nvl(v.getAbstract_())
                + "\n【关键词】" + nvl(v.getKeywords())
                + "\n【正文】（从上传文件提取的真实文本节选，可能包含参考文献）\n"
                + abbreviate(body, 12_000);
    }

    public String buildSummaryInput(List<ReviewReport> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(reports.size()).append(" 份审稿意见：\n");
        int i = 1;
        for (ReviewReport r : reports) {
            sb.append("意见").append(i++).append("：建议等级：").append(r.getRecommendation())
                    .append("；创新性").append(r.getScoreNovelty())
                    .append("/质量").append(r.getScoreQuality())
                    .append("/清晰度").append(r.getScoreClarity()).append("\n");
            if (r.getCommentsToEditor() != null && !r.getCommentsToEditor().isBlank()) {
                sb.append("给编辑：").append(r.getCommentsToEditor()).append("\n");
            }
            sb.append("给作者：").append(nvl(r.getCommentsToAuthor())).append("\n---\n");
        }
        return sb.toString();
    }

    public String buildExplainInput(List<ReviewReport> reports, ManuscriptVersion v) {
        StringBuilder sb = new StringBuilder();
        sb.append("稿件当前版本：v").append(v.getVersionNo()).append("\n专家意见原文（逐条）：\n");
        int i = 1;
        for (ReviewReport r : reports) {
            for (String line : nvl(r.getCommentsToAuthor()).split("\\n")) {
                String t = line.trim();
                if (!t.isEmpty()) {
                    sb.append("意见 ").append(i++).append("：").append(t).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // ---------------- 输出 Schema 校验 ----------------

    public JsonNode validateAndParse(String scene, String content) {
        if (content == null || content.isBlank()) throw new BizException(ErrorCode.DEP_FAILED, "模型输出为空");
        String json = stripFences(content);
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEP_FAILED, "输出不是合法 JSON");
        }
        if (node == null || !node.isObject()) throw new BizException(ErrorCode.DEP_FAILED, "模型输出必须为 JSON 对象");
        switch (scene) {
            case "EXTRACT" -> {
                requireText(node, "title");
                requireTextField(node, "abstract");
                JsonNode kw = node.get("keywords");
                if (kw == null || !kw.isArray() || kw.size() < 3 || kw.size() > 8) {
                    throw new BizException(ErrorCode.DEP_FAILED, "keywords 必须是 3-8 个元素的数组");
                }
                requireText(node, "primary_category");
                JsonNode lang = node.get("language");
                if (lang == null || !List.of("zh", "en").contains(lang.asText())) {
                    throw new BizException(ErrorCode.DEP_FAILED, "language 必须是 zh 或 en");
                }
                requireInt(node, "word_count_est");
                requireInt(node, "reference_count_est");
                if (node.get("has_ethics_statement") == null || !node.get("has_ethics_statement").isBoolean()) {
                    throw new BizException(ErrorCode.DEP_FAILED, "has_ethics_statement 必须是布尔值");
                }
                JsonNode issues = node.get("possible_issues");
                if (issues == null || !issues.isArray()) {
                    throw new BizException(ErrorCode.DEP_FAILED, "possible_issues 必须是数组");
                }
            }
            case "SUMMARY" -> {
                requireText(node, "summary");
                requireArray(node, "major_issues");
                requireArray(node, "minor_issues");
                JsonNode consensus = node.get("consensus");
                if (consensus == null || !List.of("accept", "minor", "major", "reject").contains(consensus.asText())) {
                    throw new BizException(ErrorCode.DEP_FAILED, "consensus 取值非法");
                }
                requireText(node, "dissent");
                JsonNode suggested = node.get("suggested_decision");
                if (suggested == null
                        || !List.of("ACCEPT", "REVISE", "REJECT").contains(suggested.asText().toUpperCase())) {
                    throw new BizException(ErrorCode.DEP_FAILED, "suggested_decision 取值非法");
                }
            }
            case "EXPLAIN" -> {
                JsonNode explanations = node.get("explanations");
                if (explanations == null || !explanations.isArray() || explanations.isEmpty()) {
                    throw new BizException(ErrorCode.DEP_FAILED, "explanations 必须是非空数组");
                }
                for (JsonNode e : explanations) {
                    requireText(e, "issue");
                    requireText(e, "plain");
                    requireText(e, "suggestion");
                }
                requireArray(node, "action_items");
            }
            default -> throw BizException.badParam("未知场景：" + scene);
        }
        return node;
    }

    private String stripFences(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(json)?", "").trim();
            int end = s.lastIndexOf("```");
            if (end > 0) s = s.substring(0, end).trim();
        }
        return s;
    }

    private void requireText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || !f.isTextual() || f.asText().isBlank()) {
            throw new BizException(ErrorCode.DEP_FAILED, "缺少必填文本字段：" + field);
        }
    }

    private void requireTextField(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || !f.isTextual()) {
            throw new BizException(ErrorCode.DEP_FAILED, "缺少文本字段：" + field);
        }
    }

    private void validateExtractCategory(JsonNode node) {
        String name = node.path("primary_category").asText();
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getName, name));
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.DEP_FAILED, "primary_category 不在系统学科分类中");
        }
    }

    private void requireInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || !f.canConvertToInt() || f.asInt() < 0) {
            throw new BizException(ErrorCode.DEP_FAILED, "缺少必填整数字段：" + field);
        }
    }

    private void requireArray(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || !f.isArray()) {
            throw new BizException(ErrorCode.DEP_FAILED, "缺少必填数组字段：" + field);
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
