package com.gnagnoohc.scms.domain.mileage.listener;

import com.gnagnoohc.scms.domain.mileage.service.ProgramMileageAccrualService;
import com.gnagnoohc.scms.domain.program.event.ProgramCompletionJudgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 비교과 이수 확정 이벤트를 받아 마일리지 원장 적립을 수행한다. */
@Component
@RequiredArgsConstructor
public class ProgramCompletionJudgedEventListener {

    private final ProgramMileageAccrualService programMileageAccrualService;

    /**
     * 기본 동기 이벤트 리스너로 처리하여 이벤트 발행부의 트랜잭션에 참여한다.
     * @Async 또는 AFTER_COMMIT 리스너로 바꾸면 이수 판정과 마일리지 적립의 원자성이 깨질 수 있다.
     */
    @EventListener
    public void handle(ProgramCompletionJudgedEvent event) {
        programMileageAccrualService.accrueProgramCompletion(event.applicationId());
    }
}
