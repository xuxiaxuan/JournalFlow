package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicServiceValidationTest {
    // Invalid inputs must be rejected before any mapper access.
    final PublicService service = new PublicService(null, null, null, null, null, null, null, null, null, null);

    @Test void rejectsNegativeAndExcessiveRecommendationLimits() {
        for (int limit : new int[]{-1, 0, 101}) {
            assertEquals(ErrorCode.BAD_PARAM, assertThrows(BizException.class, () -> service.recommend(null, limit)).getErrorCode());
        }
    }
    @Test void rejectsInvalidAndReversedDates() {
        assertThrows(BizException.class, () -> service.publishedPage(1, 10, null, null, "2026-02-30", null, "new"));
        assertThrows(BizException.class, () -> service.publishedPage(1, 10, null, null, "2026-10-03", "2026-10-01", "new"));
    }
    @Test void rejectsUnknownSort() {
        assertThrows(BizException.class, () -> service.publishedPage(1, 10, null, null, null, null, "invalid"));
    }
}
