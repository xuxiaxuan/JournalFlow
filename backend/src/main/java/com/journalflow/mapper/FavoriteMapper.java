package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.Favorite;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface FavoriteMapper extends BaseMapper<Favorite> {
    @Select("""
            SELECT f.manuscript_id, f.created_at, m.title, m.published_at
            FROM favorite f JOIN manuscript m ON m.id = f.manuscript_id
            WHERE f.user_id = #{userId} ORDER BY f.created_at DESC
            """)
    List<Map<String, Object>> selectMyFavorites(@Param("userId") Long userId);
}
