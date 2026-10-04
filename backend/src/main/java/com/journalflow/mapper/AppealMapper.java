package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.Appeal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface AppealMapper extends BaseMapper<Appeal> {
    @Select("""
            <script>
            SELECT a.*, u.real_name AS appellant_name,
                   d.manuscript_id AS appeal_manuscript_id, m.title AS manuscript_title
            FROM appeal a
            JOIN user u ON u.id = a.appellant_id
            LEFT JOIN editorial_decision d ON d.id = a.decision_id
            LEFT JOIN manuscript m ON m.id = d.manuscript_id
            <where>
              <if test="appellantId != null">AND a.appellant_id = #{appellantId}</if>
              <if test="status != null and status != ''">AND a.status = #{status}</if>
            </where>
            ORDER BY a.created_at DESC
            </script>
            """)
    List<Map<String, Object>> selectPage(@Param("page") Object page,
                                         @Param("appellantId") Long appellantId,
                                         @Param("status") String status);
}
