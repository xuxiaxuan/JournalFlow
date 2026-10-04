package com.journalflow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 运营统计聚合查询（管理端 ECharts 看板数据源）。
 */
public interface StatsMapper {

    @Select("SELECT status, COUNT(*) cnt FROM manuscript GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m') ym, COUNT(*) cnt
            FROM manuscript
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{months} MONTH)
            GROUP BY ym ORDER BY ym
            """)
    List<Map<String, Object>> submissionTrend(@Param("months") int months);

    @Select("""
            SELECT DATE_FORMAT(d.decided_at, '%Y-%m') ym,
                   ROUND(AVG(DATEDIFF(d.decided_at, m.created_at)), 1) avg_days,
                   SUM(d.decision = 'ACCEPT') accept_cnt,
                   SUM(d.decision = 'REJECT') reject_cnt,
                   SUM(d.decision = 'REVISE') revise_cnt
            FROM editorial_decision d JOIN manuscript m ON m.id = d.manuscript_id
            WHERE d.stage = 'REVIEW' AND d.decided_at >= DATE_SUB(CURDATE(), INTERVAL #{months} MONTH)
            GROUP BY ym ORDER BY ym
            """)
    List<Map<String, Object>> reviewCycle(@Param("months") int months);

    @Select("SELECT status, COUNT(*) cnt, ROUND(AVG(IFNULL(TIMESTAMPDIFF(HOUR, created_at, handled_at), 0)), 1) avg_hours " +
            "FROM report GROUP BY status")
    List<Map<String, Object>> reportHandling();

    @Select("""
            SELECT u.id AS author_id, u.real_name, u.affiliation,
                   COUNT(*) published_cnt, IFNULL(SUM(v.view_sum), 0) total_views
            FROM manuscript m
            JOIN user u ON u.id = m.submitter_id
            LEFT JOIN (SELECT manuscript_id, SUM(view_count) view_sum FROM view_stat GROUP BY manuscript_id) v
              ON v.manuscript_id = m.id
            WHERE m.status = 'PUBLISHED'
            GROUP BY u.id, u.real_name, u.affiliation
            ORDER BY published_cnt DESC, total_views DESC
            LIMIT #{top}
            """)
    List<Map<String, Object>> topAuthors(@Param("top") int top);

    @Select("""
            SELECT (SELECT COUNT(*) FROM user) AS user_count,
                   (SELECT COUNT(*) FROM manuscript) AS manuscript_count,
                   (SELECT COUNT(*) FROM manuscript WHERE status = 'PUBLISHED') AS published_count,
                   (SELECT COUNT(*) FROM manuscript WHERE status IN ('SUBMITTED','IN_FORMAT_REVIEW')) AS pending_format_count,
                   (SELECT COUNT(*) FROM manuscript WHERE status IN ('UNDER_REVIEW','PENDING_DECISION')) AS in_review_count,
                   (SELECT COUNT(*) FROM report WHERE status = 'PENDING') AS pending_report_count,
                   (SELECT COUNT(*) FROM appeal WHERE status = 'PENDING') AS pending_appeal_count,
                   (SELECT COUNT(*) FROM comment) AS comment_count,
                   (SELECT COUNT(*) FROM llm_task) AS llm_task_count,
                   (SELECT COUNT(*) FROM llm_task WHERE status = 'SUCCESS') AS llm_pending_confirm_count,
                   (SELECT ROUND(AVG(DATEDIFF(d.decided_at, m.created_at)), 1) FROM editorial_decision d
                     JOIN manuscript m ON m.id = d.manuscript_id
                     WHERE d.stage = 'REVIEW' AND d.decision IN ('ACCEPT','REJECT')) AS avg_review_days,
                   (SELECT ROUND(100 * SUM(d.decision = 'ACCEPT') / GREATEST(COUNT(*), 1), 1) FROM editorial_decision d
                     WHERE d.stage = 'REVIEW' AND d.decision IN ('ACCEPT','REJECT')) AS acceptance_rate
            """)
    Map<String, Object> overview();

    @Select("""
            SELECT ROUND(AVG(score_novelty), 1) avg_novelty, ROUND(AVG(score_quality), 1) avg_quality,
                   ROUND(AVG(score_clarity), 1) avg_clarity, COUNT(*) report_count
            FROM review_report
            """)
    Map<String, Object> reviewScoreAvg();
}
