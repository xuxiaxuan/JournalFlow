package com.journalflow.statemachine;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.entity.Manuscript;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManuscriptStateMachineTest {

    private final ManuscriptStateMachine stateMachine = new ManuscriptStateMachine();

    @Test
    void completesTheMainPublicationFlow() {
        Manuscript manuscript = manuscriptIn(ManuscriptStatus.DRAFT);

        transition(manuscript, ManuscriptStatus.SUBMITTED);
        transition(manuscript, ManuscriptStatus.IN_FORMAT_REVIEW);
        transition(manuscript, ManuscriptStatus.UNDER_REVIEW);
        transition(manuscript, ManuscriptStatus.PENDING_DECISION);
        transition(manuscript, ManuscriptStatus.ACCEPTED);
        transition(manuscript, ManuscriptStatus.IN_PRODUCTION);
        transition(manuscript, ManuscriptStatus.PUBLISHED);

        assertEquals(ManuscriptStatus.PUBLISHED.name(), manuscript.getStatus());
    }

    @Test
    void supportsRevisionAndSecondReview() {
        Manuscript manuscript = manuscriptIn(ManuscriptStatus.PENDING_DECISION);

        transition(manuscript, ManuscriptStatus.REVISION_REQUIRED);
        transition(manuscript, ManuscriptStatus.UNDER_REVIEW);

        assertEquals(ManuscriptStatus.UNDER_REVIEW.name(), manuscript.getStatus());
    }

    @Test
    void rejectsIllegalTransitionWithoutChangingState() {
        Manuscript manuscript = manuscriptIn(ManuscriptStatus.DRAFT);

        BizException exception = assertThrows(BizException.class,
                () -> stateMachine.transition(manuscript, ManuscriptStatus.PUBLISHED));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals(ManuscriptStatus.DRAFT.name(), manuscript.getStatus());
    }

    @Test
    void terminalStateCannotReturnToWorkflow() {
        Manuscript manuscript = manuscriptIn(ManuscriptStatus.REJECTED);

        assertThrows(BizException.class,
                () -> stateMachine.transition(manuscript, ManuscriptStatus.UNDER_REVIEW));
    }

    private void transition(Manuscript manuscript, ManuscriptStatus target) {
        stateMachine.transition(manuscript, target);
        assertEquals(target.name(), manuscript.getStatus());
    }

    private Manuscript manuscriptIn(ManuscriptStatus status) {
        Manuscript manuscript = new Manuscript();
        manuscript.setStatus(status.name());
        return manuscript;
    }
}
