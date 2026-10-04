package com.journalflow.statemachine;

import com.journalflow.common.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 稿件状态机：状态流转只允许沿规定路径，非法流转抛 409 冲突。
 * DRAFT → SUBMITTED → IN_FORMAT_REVIEW → UNDER_REVIEW → PENDING_DECISION
 *   → ACCEPTED → IN_PRODUCTION → PUBLISHED
 *   → REVISION_REQUIRED →(修回)→ UNDER_REVIEW
 * 分支终态：REJECTED / WITHDRAWN
 */
public enum ManuscriptStatus {
    DRAFT, SUBMITTED, IN_FORMAT_REVIEW, UNDER_REVIEW, PENDING_DECISION,
    ACCEPTED, IN_PRODUCTION, PUBLISHED, REVISION_REQUIRED, REJECTED, WITHDRAWN;

    private static final Map<ManuscriptStatus, Set<ManuscriptStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(DRAFT, Set.of(SUBMITTED)),
            Map.entry(SUBMITTED, Set.of(IN_FORMAT_REVIEW, WITHDRAWN)),
            Map.entry(IN_FORMAT_REVIEW, Set.of(UNDER_REVIEW, REJECTED, WITHDRAWN)),
            Map.entry(UNDER_REVIEW, Set.of(PENDING_DECISION, WITHDRAWN)),
            Map.entry(PENDING_DECISION, Set.of(ACCEPTED, REVISION_REQUIRED, REJECTED, WITHDRAWN)),
            Map.entry(REVISION_REQUIRED, Set.of(UNDER_REVIEW, WITHDRAWN)),
            Map.entry(ACCEPTED, Set.of(IN_PRODUCTION)),
            Map.entry(IN_PRODUCTION, Set.of(PUBLISHED)),
            Map.entry(PUBLISHED, Set.<ManuscriptStatus>of()),
            Map.entry(REJECTED, Set.<ManuscriptStatus>of()),
            Map.entry(WITHDRAWN, Set.<ManuscriptStatus>of())
    );

    public boolean canGo(ManuscriptStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public void checkTransition(ManuscriptStatus target) {
        if (!canGo(target)) {
            throw new BizException(com.journalflow.common.ErrorCode.CONFLICT,
                    "非法状态流转：" + this + " → " + target);
        }
    }

    public static ManuscriptStatus of(String status) {
        try {
            return ManuscriptStatus.valueOf(status);
        } catch (Exception e) {
            throw new BizException(com.journalflow.common.ErrorCode.INTERNAL, "未知稿件状态：" + status);
        }
    }
}
