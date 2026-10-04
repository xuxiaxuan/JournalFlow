package com.journalflow.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginationTest {
    @Test
    void acceptsMinimumAndMaximumBounds() {
        assertDoesNotThrow(() -> Pagination.validate(1, 1));
        assertDoesNotThrow(() -> Pagination.validate(1, 100));
    }

    @Test
    void rejectsNonPositivePage() {
        BizException error = assertThrows(BizException.class, () -> Pagination.validate(0, 10));
        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void rejectsNonPositivePageSize() {
        BizException error = assertThrows(BizException.class, () -> Pagination.validate(1, 0));
        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void rejectsOversizedPage() {
        BizException error = assertThrows(BizException.class, () -> Pagination.validate(1, 101));
        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }
}
