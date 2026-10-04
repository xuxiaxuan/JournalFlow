package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.Subscription;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface SubscriptionMapper extends BaseMapper<Subscription> {
    @Select("""
            SELECT s.*, c.name AS category_name
            FROM subscription s LEFT JOIN category c ON c.id = s.category_id
            WHERE s.user_id = #{userId} ORDER BY s.created_at DESC
            """)
    List<Map<String, Object>> selectMySubscriptions(@Param("userId") Long userId);
}
