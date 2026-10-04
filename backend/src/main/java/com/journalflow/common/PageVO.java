package com.journalflow.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageVO<T> {
    private List<T> list;
    private long total;
    private long page;
    private long size;

    public static <T> PageVO<T> of(List<T> list, long total, long page, long size) {
        return new PageVO<>(list, total, page, size);
    }
}
