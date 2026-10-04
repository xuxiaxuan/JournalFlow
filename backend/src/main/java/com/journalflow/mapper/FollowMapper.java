package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.Follow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface FollowMapper extends BaseMapper<Follow> {
    @Select("""
            SELECT f.followee_id AS user_id, u.real_name, u.affiliation, u.research_fields, f.created_at
            FROM follow f JOIN user u ON u.id = f.followee_id
            WHERE f.follower_id = #{userId} ORDER BY f.created_at DESC
            """)
    List<Map<String, Object>> selectMyFollowing(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM follow WHERE follower_id = #{userId}")
    long countFollowing(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM follow WHERE followee_id = #{userId}")
    long countFollower(@Param("userId") Long userId);
}
