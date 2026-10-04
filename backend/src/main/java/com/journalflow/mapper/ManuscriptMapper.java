package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.Manuscript;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ManuscriptMapper extends BaseMapper<Manuscript> {

    /**
     * 公开已出版稿件组合分页查询：关键词/分类/日期范围 + 热度或时间排序。
     * 返回字段含分类名、提交人、作者快照串、累计浏览/下载量。
     */
    @Select("""
            <script>
            SELECT m.id, m.title, m.abstract AS abstract_, m.keywords, m.category_id, m.status, m.submitter_id,
                   m.volume, m.issue, m.pages, m.doi, m.published_at, m.created_at,
                   c.name AS category_name, u.real_name AS submitter_name,
                   IFNULL(v.view_sum, 0) AS view_sum, IFNULL(v.download_sum, 0) AS download_sum,
                   (SELECT GROUP_CONCAT(ma.author_name SEPARATOR ', ') FROM manuscript_author ma
                     WHERE ma.manuscript_id = m.id ORDER BY ma.author_order) AS author_names
            FROM manuscript m
            LEFT JOIN category c ON c.id = m.category_id
            LEFT JOIN user u ON u.id = m.submitter_id
            LEFT JOIN (SELECT manuscript_id, SUM(view_count) view_sum, SUM(download_count) download_sum
                       FROM view_stat GROUP BY manuscript_id) v ON v.manuscript_id = m.id
            WHERE m.status = 'PUBLISHED'
            <if test="keyword != null and keyword != ''">
              AND (m.title LIKE CONCAT('%', #{keyword}, '%')
                   OR m.abstract LIKE CONCAT('%', #{keyword}, '%')
                   OR m.keywords LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="categoryId != null">AND (m.category_id = #{categoryId} OR c.parent_id = #{categoryId})</if>
            <if test="dateFrom != null">AND m.published_at &gt;= #{dateFrom}</if>
            <if test="dateTo != null">AND m.published_at &lt; #{dateTo}</if>
            <choose>
              <when test="sortBy == 'hot'">ORDER BY view_sum DESC, m.published_at DESC</when>
              <otherwise>ORDER BY m.published_at DESC</otherwise>
            </choose>
            </script>
            """)
    List<Map<String, Object>> selectPublishedPage(@Param("page") Object page,
                                                  @Param("keyword") String keyword,
                                                  @Param("categoryId") Long categoryId,
                                                  @Param("dateFrom") String dateFrom,
                                                  @Param("dateTo") String dateTo,
                                                  @Param("sortBy") String sortBy);

    /** 按订阅条件匹配的可推送新出版文章：订阅学科一致，或关键词命中标题/摘要/关键词 */
    @Select("""
            SELECT DISTINCT m.id, m.title
            FROM manuscript m
            JOIN subscription s
              ON (s.category_id IS NOT NULL AND s.category_id = m.category_id)
              OR (s.keyword IS NOT NULL AND (m.title LIKE CONCAT('%', s.keyword, '%')
                  OR m.abstract LIKE CONCAT('%', s.keyword, '%')
                  OR m.keywords LIKE CONCAT('%', s.keyword, '%')))
            WHERE s.user_id = #{userId} AND m.id = #{manuscriptId}
            """)
    List<Map<String, Object>> selectMatchingSubscribers(@Param("userId") Long userId,
                                                        @Param("manuscriptId") Long manuscriptId);

    @Select("SELECT DISTINCT s.user_id FROM subscription s " +
            "JOIN manuscript m ON m.id = #{manuscriptId} " +
            "WHERE (s.category_id IS NOT NULL AND s.category_id = m.category_id) " +
            "OR (s.keyword IS NOT NULL AND (m.title LIKE CONCAT('%', s.keyword, '%') " +
            "OR m.abstract LIKE CONCAT('%', s.keyword, '%') " +
            "OR m.keywords LIKE CONCAT('%', s.keyword, '%')))")
    List<Long> selectSubscriberUserIds(@Param("manuscriptId") Long manuscriptId);
}
