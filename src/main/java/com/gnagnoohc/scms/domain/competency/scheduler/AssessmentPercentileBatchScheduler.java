package com.gnagnoohc.scms.domain.competency.scheduler;

import com.gnagnoohc.scms.domain.competency.service.AssessmentPercentileBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssessmentPercentileBatchScheduler {

    private final AssessmentPercentileBatchService assessmentPercentileBatchService;

    /** 매분 정각에 응시기간(ends_at)이 지났지만 아직 백분위가 계산되지 않은 회차를 찾아 처리한다. */
    @Scheduled(cron = "0 * * * * *")
    public void calculatePercentiles() {
        int processedCount = assessmentPercentileBatchService.calculatePercentilesForEndedRounds();
        if (processedCount > 0) {
            log.info("핵심역량 백분위 산출 배치 완료. round={}건", processedCount);
        }
    }
}
