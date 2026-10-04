package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.OperationLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface OperationLogMapper extends BaseMapper<OperationLog> {
    @Select("""
            <script>
            SELECT l.*, u.real_name AS user_name, u.username
            FROM operation_log l LEFT JOIN user u ON u.id = l.user_id
            <where>
              <if test="userId != null">AND l.user_id = #{userId}</if>
              <if test="action != null and action != ''">AND l.action LIKE CONCAT('%', #{action}, '%')</if>
              <if test="success != null">AND l.success = #{success}</if>
              <if test="dateFrom != null">AND l.created_at &gt;= #{dateFrom}</if>
              <if test="dateTo != null">AND l.created_at &lt; #{dateTo}</if>
            </where>
            ORDER BY l.created_at DESC
            </script>
            """)
    List<Map<String, Object>> selectPage(@Param("page") Object page,
                                         @Param("userId") Long userId,
                                         @Param("action") String action,
                                         @Param("success") Integer success,
                                         @Param("dateFrom") String dateFrom,
                                         @Param("dateTo") String dateTo);
}
