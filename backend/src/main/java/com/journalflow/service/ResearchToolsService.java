package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.*;
import com.journalflow.common.BizException;
import com.journalflow.entity.*;
import com.journalflow.mapper.*;
import com.journalflow.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Read-only evidence/diff tools and optional append-only review responses. */
@Service
@RequiredArgsConstructor
public class ResearchToolsService {
    private final ManuscriptService manuscripts;
    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptVersionMapper versions;
    private final ReviewAssignmentMapper assignments;
    private final ReviewResponseMapper responses;
    private final LlmTaskMapper tasks;
    private final ManuscriptTextExtractor extractor;

    private Manuscript member(Long id, UserDetailsImpl user) {
        Manuscript m = manuscriptMapper.selectById(id);
        if (m == null) throw BizException.notFound("稿件不存在");
        if (user == null || (!editor(user) && !user.getId().equals(m.getSubmitterId()) && assignments.selectCount(
                new LambdaQueryWrapper<ReviewAssignment>().eq(ReviewAssignment::getManuscriptId, id)
                        .eq(ReviewAssignment::getReviewerId, user.getId())) == 0)) throw BizException.forbidden("仅稿件作者、编辑及已分配审稿人可使用辅助工具");
        return m;
    }
    private boolean editor(UserDetailsImpl user) {
        return user != null && (user.getRoleCodes().contains("EDITOR") || user.getRoleCodes().contains("ADMIN"));
    }
    private ManuscriptVersion version(Long manuscriptId, Long versionId) {
        ManuscriptVersion v = versions.selectById(versionId);
        if (v == null || !manuscriptId.equals(v.getManuscriptId())) throw BizException.notFound("版本不属于该稿件");
        return v;
    }

    public Map<String, Object> compare(Long id, Long from, Long to, UserDetailsImpl user) {
        member(id, user);
        ManuscriptVersion a = version(id, from), b = version(id, to);
        String oldText = extractor.extract(a), newText = extractor.extract(b);
        if (oldText.isBlank() || newText.isBlank()) throw BizException.badParam("版本正文无法提取，支持可读取文本的 PDF、DOCX、TeX；扫描件需先进行 OCR");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromVersion", a.getVersionNo()); result.put("toVersion", b.getVersionNo());
        result.put("fromHash", a.getFileHash()); result.put("toHash", b.getFileHash());
        result.put("changes", diff(oldText, newText));
        result.put("limited", oldText.length() >= 30000 || newText.length() >= 30000 || lines(oldText).size() > 400 || lines(newText).size() > 400);
        result.put("scope", "按提取后的非空文本行对比，最多 30000 字符和 400 行，不包含排版差异");
        return result;
    }

    static List<String> lines(String text) { return Arrays.stream(text.split("\\R")).map(String::strip).filter(s -> !s.isBlank()).toList(); }
    static List<Map<String, Object>> diff(String oldText, String newText) {
        List<String> a = lines(oldText).stream().limit(400).toList(), b = lines(newText).stream().limit(400).toList();
        int[][] lcs = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size()-1; i >= 0; i--) for (int j = b.size()-1; j >= 0; j--)
            lcs[i][j] = a.get(i).equals(b.get(j)) ? 1+lcs[i+1][j+1] : Math.max(lcs[i+1][j], lcs[i][j+1]);
        List<Map<String, Object>> changes = new ArrayList<>(); int i=0, j=0;
        while (i < a.size() || j < b.size()) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (i < a.size() && j < b.size() && a.get(i).equals(b.get(j))) {
                row.put("type", "SAME"); row.put("text", a.get(i)); row.put("oldLine", ++i); row.put("newLine", ++j);
            } else if (i < a.size() && (j == b.size() || lcs[i+1][j] >= lcs[i][j+1])) {
                row.put("type", "REMOVED"); row.put("text", a.get(i)); row.put("oldLine", ++i);
            } else { row.put("type", "ADDED"); row.put("text", b.get(j)); row.put("newLine", ++j); }
            changes.add(row);
        }
        return changes;
    }

    public Map<String, Object> evidence(Long taskId, UserDetailsImpl user) {
        if (!editor(user)) throw BizException.forbidden("仅编辑可查看元数据抽取溯源");
        LlmTask task = tasks.selectById(taskId);
        if (task == null) throw BizException.notFound("AI 任务不存在");
        if (!"EXTRACT".equals(task.getScene())) throw BizException.badParam("仅元数据抽取任务支持正文溯源");
        ManuscriptVersion v = version(task.getManuscriptId(), task.getVersionId());
        String text = extractor.extract(v);
        if (task.getParsedOutput() == null || task.getParsedOutput().isBlank()) throw BizException.badParam("该任务尚无可核对的结构化结果");
        JsonNode output;
        try { output = new ObjectMapper().readTree(task.getParsedOutput() == null ? "{}" : task.getParsedOutput()); }
        catch (Exception ex) { throw BizException.badParam("该任务尚无可核对的结构化结果"); }
        List<Map<String, Object>> fields = new ArrayList<>();
        if (output == null || !output.isObject()) throw BizException.badParam("结构化结果格式不可用");
        output.fields().forEachRemaining(entry -> {
            if (entry.getValue().isArray()) {
                int index=0;
                for (JsonNode item : entry.getValue()) {
                    if (fields.size() >= 100) break;
                    fields.add(locate(entry.getKey()+"["+(index++)+"]", item, text, v));
                }
            } else if (fields.size() < 100) fields.add(locate(entry.getKey(), entry.getValue(), text, v));
        });
        return Map.of("taskId", taskId, "versionId", v.getId(), "versionNo", v.getVersionNo(), "fileHash", Objects.toString(v.getFileHash(), ""),
                "fields", fields, "textAvailable", !text.isBlank(), "scope", "依据任务原始解析结果与该版本文本忽略空白匹配；表单快照单独标注。匹配不代表事实正确，未匹配字段需人工核实。正文最多提取30000字符。");
    }
    static Map<String, Object> locate(String field, JsonNode value, String text, ManuscriptVersion v) {
        Map<String, Object> row = new LinkedHashMap<>(); row.put("field", field); row.put("value", value);
        boolean directField = field.equals("title") || field.equals("abstract") || field.startsWith("keywords[");
        String needle = directField && value.isTextual() ? compact(value.asText()) : "";
        List<String> body = lines(text);
        if (!needle.isBlank()) for (int i=0; i<body.size(); i++) {
            if (compact(body.get(i)).contains(needle)) {
                row.put("source", "BODY"); row.put("line", i+1); row.put("quote", body.get(i)); return row;
            }
        }
        String snapshot = field.equals("title") ? v.getTitle() : field.equals("abstract") ? v.getAbstract_() : field.startsWith("keywords[") ? v.getKeywords() : "";
        if (!needle.isBlank() && compact(Objects.toString(snapshot, "")).contains(needle)) {
            row.put("source", "VERSION_METADATA"); row.put("quote", snapshot);
        } else { row.put("source", "UNVERIFIED"); row.put("quote", "未找到直接文本依据；估计值、分类及归纳判断需人工核实"); }
        return row;
    }
    private static String compact(String s) { return s.replaceAll("\\s+", ""); }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> visibleReports(Long id, UserDetailsImpl user) {
        member(id, user);
        return (List<Map<String, Object>>) manuscripts.detail(id, user).getOrDefault("reports", List.of());
    }
    public List<Map<String, Object>> checklist(Long id, UserDetailsImpl user) {
        List<Map<String, Object>> visible = visibleReports(id, user);
        if (visible.isEmpty()) return List.of();
        var history = responses.selectList(new LambdaQueryWrapper<ReviewResponse>().eq(ReviewResponse::getManuscriptId, id).orderByAsc(ReviewResponse::getId));
        List<Map<String, Object>> items = new ArrayList<>();
        for (var report : visible) {
            Long reportId = ((Number) report.get("id")).longValue();
            List<String> issues = lines(Objects.toString(report.get("commentsToAuthor"), ""));
            for (int index=0; index<issues.size(); index++) {
                final int point=index;
                List<ReviewResponse> revisions = history.stream().filter(r -> reportId.equals(r.getReportId()) && point == r.getIssueIndex()).toList();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("reportId", reportId); item.put("issueIndex", index); item.put("issueText", issues.get(index));
                item.put("reportVersionId", report.get("versionId")); item.put("history", revisions);
                item.put("latest", revisions.isEmpty() ? null : revisions.get(revisions.size()-1)); items.add(item);
            }
        }
        return items;
    }

    @Transactional
    public ReviewResponse save(Long id, Long reportId, int index, String reply, String location, Long versionId, Long baseId, UserDetailsImpl user) {
        Manuscript m = member(id, user);
        if (!user.getId().equals(m.getSubmitterId())) throw BizException.forbidden("仅投稿人可填写回应");
        var item = issue(id, reportId, index, user);
        if (versionId != null) version(id, versionId);
        if (reply == null || reply.length() > 5000 || location == null || location.length() > 1000) throw BizException.badParam("回应最长5000字，修改位置最长1000字");
        responses.lockManuscript(id);
        ReviewResponse last = latest(id, reportId, index);
        if (!Objects.equals(baseId, last == null ? null : last.getId())) throw BizException.conflict("回应已被更新，请刷新后再保存");
        ReviewResponse row = new ReviewResponse(); row.setManuscriptId(id); row.setReportId(reportId); row.setIssueIndex(index);
        row.setIssueText((String)item.get("issueText")); row.setResponseText(reply); row.setChangeLocation(location); row.setVersionId(versionId);
        row.setStatus(reply.isBlank() ? "TODO" : "RESPONDED"); row.setCreatedBy(user.getId()); row.setCreatedAt(LocalDateTime.now()); responses.insert(row); return row;
    }
    @Transactional
    public ReviewResponse confirm(Long id, Long responseId, UserDetailsImpl user) {
        member(id, user);
        if (!editor(user)) throw BizException.forbidden("仅编辑可确认回应");
        responses.lockManuscript(id);
        ReviewResponse row = responses.selectById(responseId);
        if (row == null || !id.equals(row.getManuscriptId())) throw BizException.notFound("回应不存在");
        issue(id, row.getReportId(), row.getIssueIndex(), user);
        if (!responseId.equals(latest(id, row.getReportId(), row.getIssueIndex()).getId())) throw BizException.conflict("请确认最新回应");
        if (!"RESPONDED".equals(row.getStatus())) throw BizException.conflict("仅已填写且未确认的回应可确认");
        row.setId(null); row.setStatus("CONFIRMED"); row.setCreatedBy(user.getId()); row.setCreatedAt(LocalDateTime.now()); responses.insert(row); return row;
    }
    private ReviewResponse latest(Long id, Long report, int index) {
        return responses.selectOne(new LambdaQueryWrapper<ReviewResponse>().eq(ReviewResponse::getManuscriptId, id)
                .eq(ReviewResponse::getReportId, report).eq(ReviewResponse::getIssueIndex, index).orderByDesc(ReviewResponse::getId).last("LIMIT 1 FOR UPDATE"));
    }
    private Map<String, Object> issue(Long id, Long report, int index, UserDetailsImpl user) {
        return checklist(id, user).stream().filter(i -> report.equals(i.get("reportId")) && index == (Integer)i.get("issueIndex"))
                .findFirst().orElseThrow(() -> BizException.notFound("意见不存在或当前身份不可见"));
    }
}
