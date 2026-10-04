package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.Report;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ReportMapper extends BaseMapper<Report> {
    @Select("""
            <script>
            SELECT r.*, u.real_name AS reporter_name
            FROM report r JOIN user u ON u.id = r.reporter_id
            <where>
              <if test="reporterId != null">AND r.reporter_id = #{reporterId}</if>
              <if test="status != null and status != ''">AND r.status = #{status}</if>
              <if test="targetType != null and targetType != ''">AND r.target_type = #{targetType}</if>
            </where>
            ORDER BY r.created_at DESC
            </script>
            """)
    List<Map<String, Object>> selectPage(@Param("page") Object page,
                                         @Param("reporterId") Long reporterId,
                                         @Param("status") String status,
                                         @Param("targetType") String targetType);
}
