package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.ManuscriptVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ManuscriptVersionMapper extends BaseMapper<ManuscriptVersion> {
    @Select("SELECT * FROM manuscript_version WHERE manuscript_id = #{manuscriptId} ORDER BY version_no DESC LIMIT 1")
    @org.apache.ibatis.annotations.ResultMap("mybatis-plus_ManuscriptVersion")
    ManuscriptVersion selectLatest(@Param("manuscriptId") Long manuscriptId);
}
