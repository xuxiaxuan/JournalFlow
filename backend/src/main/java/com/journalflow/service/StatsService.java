package com.journalflow.service;

import com.journalflow.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营统计看板数据（管理员/编辑端 ECharts）。
 */
@Service
@RequiredArgsConstructor
public class StatsService {
    private final StatsMapper statsMapper;

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>(statsMapper.overview());
        data.putAll(statsMapper.reviewScoreAvg());
        return data;
    }

    public List<Map<String, Object>> submissionTrend(int months) {
        List<Map<String, Object>> rows = statsMapper.submissionTrend(Math.min(Math.max(months, 1), 24));
        return fillMonths(rows, "ym", "cnt", months);
    }

    public List<Map<String, Object>> reviewCycle(int months) {
        return statsMapper.reviewCycle(Math.min(Math.max(months, 1), 24));
    }

    public List<Map<String, Object>> reportHandling() {
        return statsMapper.reportHandling();
    }

    public List<Map<String, Object>> topAuthors(int top) {
        return statsMapper.topAuthors(Math.min(Math.max(top, 1), 20));
    }

    /** 补齐无数据的月份，保证趋势图连续 */
    private List<Map<String, Object>> fillMonths(List<Map<String, Object>> rows, String keyField,
                                                 String countField, int months) {
        Map<String, Object> byMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byMonth.put(String.valueOf(row.get(keyField)), row.get(countField));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cur = LocalDate.now().withDayOfMonth(1);
        for (int i = months - 1; i >= 0; i--) {
            LocalDate m = cur.minusMonths(i);
            String ym = "%d-%02d".formatted(m.getYear(), m.getMonthValue());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(keyField, ym);
            row.put(countField, byMonth.getOrDefault(ym, 0));
            result.add(row);
        }
        return result;
    }
}
