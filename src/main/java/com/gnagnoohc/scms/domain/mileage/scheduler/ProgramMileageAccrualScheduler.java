package com.gnagnoohc.scms.domain.mileage.scheduler;

import com.gnagnoohc.scms.domain.mileage.service.ProgramMileageAccrualService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 비교과 도메인을 수정하지 않고 이수 완료 건을 주기적으로 마일리지에 반영한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramMileageAccrualScheduler {

    private final ProgramMileageAccrualService programMileageAccrualService;

    /** 기본 1분마다 이수 완료 후 미적립 신청을 확인한다. */
    @Scheduled(fixedDelayString = "${app.mileage.auto-accrual.fixed-delay-ms:60000}")
    public void accrueCompletedPrograms() {
        int accruedCount = programMileageAccrualService.accruePendingProgramCompletions();
        if (accruedCount > 0) {
            log.info("비교과 프로그램 마일리지 자동 적립 완료. count={}", accruedCount);
        }
    }
}
