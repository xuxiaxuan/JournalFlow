package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface CommentMapper extends BaseMapper<Comment> {
    @Select("""
            SELECT cm.*, u.real_name AS user_name, u.affiliation AS user_affiliation,
                   p.content AS parent_content, pu.real_name AS parent_user_name
            FROM comment cm
            JOIN user u ON u.id = cm.user_id
            LEFT JOIN comment p ON p.id = cm.parent_id AND p.status = 1
            LEFT JOIN user pu ON pu.id = p.user_id
            WHERE cm.target_type = #{targetType} AND cm.target_id = #{targetId} AND cm.status = 1
            ORDER BY cm.created_at
            """)
    List<Map<String, Object>> selectVisibleByTarget(@Param("targetType") String targetType,
                                                    @Param("targetId") Long targetId);
}
