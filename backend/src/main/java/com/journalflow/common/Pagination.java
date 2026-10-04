package com.journalflow.common;

/** 统一约束分页参数，避免无效页码和超大查询。 */
public final class Pagination {
    public static final long MAX_PAGE_SIZE = 100;

    private Pagination() {
    }

    public static void validate(long page, long size) {
        if (page < 1) {
            throw BizException.badParam("页码必须大于等于 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BizException.badParam("每页数量必须在 1-" + MAX_PAGE_SIZE + " 之间");
        }
    }
}
