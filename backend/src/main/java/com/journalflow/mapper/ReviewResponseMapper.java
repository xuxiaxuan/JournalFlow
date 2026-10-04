package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.ReviewResponse;
import org.apache.ibatis.annotations.*;

public interface ReviewResponseMapper extends BaseMapper<ReviewResponse> {
    @Select("SELECT id FROM manuscript WHERE id = #{id} FOR UPDATE")
    Long lockManuscript(@Param("id") Long id);
}
