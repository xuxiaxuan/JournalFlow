package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.ReviewAssignment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ReviewAssignmentMapper extends BaseMapper<ReviewAssignment> {

    /** 审稿人视角：我的审稿任务（带稿件标题、版本号、编辑名、我的意见 ID） */
    @Select("""
            <script>
            SELECT ra.id, ra.manuscript_id, ra.version_id, ra.reviewer_id, ra.invited_by, ra.invited_at,
                   ra.due_at, ra.status, mv.version_no, mv.title AS manuscript_title,
                   mu.real_name AS reviewer_name, eu.real_name AS editor_name,
                   rr.id AS report_id, rr.recommendation
            FROM review_assignment ra
            JOIN manuscript_version mv ON mv.id = ra.version_id
            JOIN user mu ON mu.id = ra.reviewer_id
            JOIN user eu ON eu.id = ra.invited_by
            LEFT JOIN review_report rr ON rr.assignment_id = ra.id
            WHERE ra.reviewer_id = #{reviewerId}
            <if test="status != null and status != ''">AND ra.status = #{status}</if>
            ORDER BY ra.invited_at DESC
            </script>
            """)
    List<Map<String, Object>> selectMyAssignments(@Param("page") Object page,
                                                  @Param("reviewerId") Long reviewerId,
                                                  @Param("status") String status);

    /** 编辑视角：某稿件某版本的审稿任务（含审稿人姓名、意见摘要） */
    @Select("""
            SELECT ra.id, ra.manuscript_id, ra.version_id, ra.reviewer_id, ra.invited_at, ra.due_at,
                   ra.status, mu.real_name AS reviewer_name, mu.research_fields,
                   rr.id AS report_id, rr.recommendation,
                   rr.score_novelty, rr.score_quality, rr.score_clarity
            FROM review_assignment ra
            JOIN user mu ON mu.id = ra.reviewer_id
            LEFT JOIN review_report rr ON rr.assignment_id = ra.id
            WHERE ra.manuscript_id = #{manuscriptId} AND ra.version_id = #{versionId}
            ORDER BY ra.invited_at
            """)
    List<Map<String, Object>> selectByManuscriptVersion(@Param("manuscriptId") Long manuscriptId,
                                                        @Param("versionId") Long versionId);
}
