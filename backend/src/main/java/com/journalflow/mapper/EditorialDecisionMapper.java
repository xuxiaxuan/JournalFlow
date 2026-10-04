package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.EditorialDecision;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface EditorialDecisionMapper extends BaseMapper<EditorialDecision> {
    @Select("""
            SELECT d.*, u.real_name AS decider_name
            FROM editorial_decision d LEFT JOIN user u ON u.id = d.decided_by
            WHERE d.manuscript_id = #{manuscriptId} ORDER BY d.decided_at
            """)
    List<Map<String, Object>> selectByManuscript(@Param("manuscriptId") Long manuscriptId);
}
