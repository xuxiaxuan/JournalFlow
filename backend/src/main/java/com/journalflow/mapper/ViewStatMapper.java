package com.journalflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.journalflow.entity.ViewStat;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ViewStatMapper extends BaseMapper<ViewStat> {
    @Update("""
            INSERT INTO view_stat (manuscript_id, view_count, download_count, stat_date)
            VALUES (#{manuscriptId}, #{viewInc}, 0, CURDATE())
            ON DUPLICATE KEY UPDATE view_count = view_count + #{viewInc}
            """)
    int incrementView(@Param("manuscriptId") Long manuscriptId, @Param("viewInc") int viewInc);

    @Update("""
            INSERT INTO view_stat (manuscript_id, view_count, download_count, stat_date)
            VALUES (#{manuscriptId}, 0, #{downloadInc}, CURDATE())
            ON DUPLICATE KEY UPDATE download_count = download_count + #{downloadInc}
            """)
    int incrementDownload(@Param("manuscriptId") Long manuscriptId, @Param("downloadInc") int downloadInc);
}
