package com.gnagnoohc.scms.domain.program.scheduler;

import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProgramStatusScheduler {

    private final ExtracurricularProgramRepository programRepository;

    // 매분 정각에 실행. 모집중(DRAFT)→운영중(OPERATING)을 먼저 처리한 뒤 운영중→종료(CLOSED)를 처리해야
    // 스케줄러가 한동안 멈췄다 재개된 경우에도(두 마감 시각을 모두 지난 프로그램이 있는 경우) 같은 사이클
    // 안에서 최종 상태(CLOSED)까지 한 번에 따라잡을 수 있다.
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void transitionProgramStatuses() {
        Instant now = Instant.now();
        int recruitingToOperating = programRepository.transitionRecruitingToOperating(now);
        int operatingToClosed = programRepository.transitionOperatingToClosed(now);

        if (recruitingToOperating > 0 || operatingToClosed > 0) {
            log.info("프로그램 상태 자동 전환 - 모집중→운영중: {}건, 운영중→종료: {}건",
                    recruitingToOperating, operatingToClosed);
        }
    }
}
