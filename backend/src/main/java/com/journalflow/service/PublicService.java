package com.journalflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.journalflow.common.BizException;
import com.journalflow.common.PageVO;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.ManuscriptAuthor;
import com.journalflow.entity.ManuscriptVersion;
import com.journalflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.*;
import java.util.stream.Collectors;

/**
 * 公开阅读：检索、详情（阅读计数）、推荐、下载。
 */
@Service
@RequiredArgsConstructor
public class PublicService {
    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptVersionMapper versionMapper;
    private final ManuscriptAuthorMapper authorMapper;
    private final ManuscriptTagMapper manuscriptTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ViewStatMapper viewStatMapper;
    private final FavoriteMapper favoriteMapper;
    private final FollowMapper followMapper;
    private final SubscriptionMapper subscriptionMapper;

    public PageVO<Map<String, Object>> publishedPage(long page, long size, String keyword,
                                                     Long categoryId, String dateFrom, String dateTo, String sortBy) {
        com.journalflow.common.Pagination.validate(page, size);
        try {
            java.time.LocalDate from = dateFrom == null ? null : java.time.LocalDate.parse(dateFrom);
            java.time.LocalDate to = dateTo == null ? null : java.time.LocalDate.parse(dateTo);
            if (from != null && to != null && !from.isBefore(to)) {
                throw BizException.badParam("起始日期必须早于结束边界日期（结束边界不包含当天）");
            }
        } catch (java.time.format.DateTimeParseException e) {
            throw BizException.badParam("日期格式应为 yyyy-MM-dd，且必须是有效日期");
        }
        if (!"new".equals(sortBy) && !"hot".equals(sortBy)) throw BizException.badParam("不支持的排序方式");
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        List<Map<String, Object>> rows = manuscriptMapper.selectPublishedPage(
                p, keyword, categoryId, dateFrom, dateTo, sortBy);
        return PageVO.of(rows, p.getTotal(), page, size);
    }

    public Map<String, Object> detail(Long id, Long viewerId) {
        Manuscript m = manuscriptMapper.selectById(id);
        if (m == null || !"PUBLISHED".equals(m.getStatus())) {
            throw BizException.notFound("文章不存在或未发布");
        }
        // 阅读计数：作者本人查看不计
        if (viewerId == null || !viewerId.equals(m.getSubmitterId())) {
            viewStatMapper.incrementView(id, 1);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.getId());
        data.put("title", m.getTitle());
        data.put("abstract", m.getAbstract_());
        data.put("keywords", m.getKeywords());
        data.put("category", m.getCategoryId() == null ? null : categoryMapper.selectById(m.getCategoryId()));
        data.put("volume", m.getVolume());
        data.put("issue", m.getIssue());
        data.put("pages", m.getPages());
        data.put("doi", m.getDoi());
        data.put("publishedAt", m.getPublishedAt());
        data.put("authors", authorMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ManuscriptAuthor>()
                .eq(ManuscriptAuthor::getManuscriptId, id).orderByAsc(ManuscriptAuthor::getAuthorOrder)));
        data.put("tags", tagMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.journalflow.entity.Tag>()
                .inSql(com.journalflow.entity.Tag::getId, "SELECT tag_id FROM manuscript_tag WHERE manuscript_id = " + id)));
        data.put("submitterId", m.getSubmitterId());
        data.put("versions", versionMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ManuscriptVersion>()
                .eq(ManuscriptVersion::getManuscriptId, id).orderByDesc(ManuscriptVersion::getVersionNo))
                .stream().map(v -> Map.of("id", v.getId(), "versionNo", v.getVersionNo(), "fileName", v.getFileName())).toList());
        // 汇总阅读/下载数
        Map<String, Object> sums = viewStatTotal(id);
        data.put("viewTotal", sums.get("views"));
        data.put("downloadTotal", sums.get("downloads"));
        if (viewerId != null) {
            data.put("favorited", favoriteMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.journalflow.entity.Favorite>()
                    .eq(com.journalflow.entity.Favorite::getUserId, viewerId)
                    .eq(com.journalflow.entity.Favorite::getManuscriptId, id)) > 0);
            data.put("followingAuthor", followMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.journalflow.entity.Follow>()
                    .eq(com.journalflow.entity.Follow::getFollowerId, viewerId)
                    .eq(com.journalflow.entity.Follow::getFolloweeId, m.getSubmitterId())) > 0);
        }
        return data;
    }

    public Map<String, Object> viewStatTotal(Long manuscriptId) {
        Map<String, Object> sums = new LinkedHashMap<>();
        int views = 0, downloads = 0;
        for (com.journalflow.entity.ViewStat vs : viewStatMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.journalflow.entity.ViewStat>()
                        .eq(com.journalflow.entity.ViewStat::getManuscriptId, manuscriptId))) {
            views += vs.getViewCount() == null ? 0 : vs.getViewCount();
            downloads += vs.getDownloadCount() == null ? 0 : vs.getDownloadCount();
        }
        sums.put("views", views);
        sums.put("downloads", downloads);
        return sums;
    }

    /** 推荐：登录用户按订阅学科/关键词/关注作者加权，匿名按热度；排除本人稿件 */
    public List<Map<String, Object>> recommend(Long viewerId, int limit) {
        if (limit < 1 || limit > 100) throw BizException.badParam("推荐数量应在 1 到 100 之间");
        PageVO<Map<String, Object>> all = publishedPage(1, 100, null, null, null, null, "hot");
        List<Map<String, Object>> candidates = new ArrayList<>(all.getList());

        Set<Long> subscribedCategories = new HashSet<>();
        Set<String> keywords = new HashSet<>();
        Set<Long> followedAuthors = new HashSet<>();
        if (viewerId != null) {
            for (Map<String, Object> s : subscriptionMapper.selectMySubscriptions(viewerId)) {
                if (s.get("category_id") != null) subscribedCategories.add(((Number) s.get("category_id")).longValue());
                if (s.get("keyword") != null) keywords.add(String.valueOf(s.get("keyword")).toLowerCase(Locale.ROOT));
            }
            for (Map<String, Object> f : followMapper.selectMyFollowing(viewerId)) {
                followedAuthors.add(((Number) f.get("user_id")).longValue());
            }
        }
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            Long submitterId = row.get("submitter_id") == null ? null : ((Number) row.get("submitter_id")).longValue();
            if (viewerId != null && viewerId.equals(submitterId)) {
                continue; // 不推荐本人稿件
            }
            double score = viewScore(row);
            if (viewerId != null) {
                Long categoryId = row.get("category_id") == null ? null : ((Number) row.get("category_id")).longValue();
                if (subscribedCategories.contains(categoryId)) score += 3;
                String text = (str(row.get("title")) + str(row.get("keywords"))).toLowerCase(Locale.ROOT);
                for (String kw : keywords) {
                    if (!kw.isBlank() && text.contains(kw)) score += 2;
                }
                if (followedAuthors.contains(submitterId)) score += 2;
            }
            row.put("score", score);
            scored.add(row);
        }
        scored.sort(Comparator.comparingDouble((Map<String, Object> r) -> ((Number) r.get("score")).doubleValue()).reversed());
        return scored.subList(0, Math.min(limit, scored.size()));
    }

    private double viewScore(Map<String, Object> row) {
        Object v = row.get("view_sum");
        return v == null ? 0 : ((Number) v).doubleValue() / 50.0;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
