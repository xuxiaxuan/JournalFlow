package com.journalflow.statemachine;

import com.journalflow.entity.Manuscript;
import org.springframework.stereotype.Component;

/**
 * 状态机执行器：校验并应用状态流转（修改入参实体，由调用方持久化）。
 */
@Component
public class ManuscriptStateMachine {

    public void transition(Manuscript manuscript, ManuscriptStatus target) {
        ManuscriptStatus current = ManuscriptStatus.of(manuscript.getStatus());
        current.checkTransition(target);
        manuscript.setStatus(target.name());
    }

    /**
     * 治理/申诉场景的特殊流转（举报下架、申诉翻案等），
     * 不走常规状态机路径，但要求显式声明以留痕。
     */
    public void forceTransition(Manuscript manuscript, ManuscriptStatus target, String reason) {
        manuscript.setStatus(target.name());
    }
}
