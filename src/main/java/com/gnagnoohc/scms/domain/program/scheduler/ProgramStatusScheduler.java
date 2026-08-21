package com.gnagnoohc.scms.domain.program.scheduler;

import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
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
    private final ProgramApplicationRepository applicationRepository;

    // 매분 정각에 실행. 모집중(DRAFT)→운영중(OPERATING)→종료(CLOSED)→이수 판정 순서로 처리해야
    // 스케줄러가 한동안 멈췄다 재개된 경우에도(여러 단계를 모두 지난 프로그램이 있는 경우) 같은 사이클
    // 안에서 최종 상태(이수 판정까지)를 한 번에 따라잡을 수 있다.
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void transitionProgramStatuses() {
        Instant now = Instant.now();
        int recruitingToOperating = programRepository.transitionRecruitingToOperating(now);
        int operatingToClosed = programRepository.transitionOperatingToClosed(now);
        // 방금(또는 이전 사이클에) CLOSED로 전환됐지만 아직 이수 판정이 안 된 승인 건을 판정한다.
        // judgeCompletion 자체가 completion_status IS NULL 조건으로 멱등하므로 매분 호출해도 안전하다.
        // (이수번호(certificate_no) 채번도 이 안에서 함께 처리된다 — ProgramApplicationRepository.judgeCompletion 참고.)
        int completionJudged = applicationRepository.judgeCompletion(now);
        // TODO: 마일리지 자동 적립 연동 (마일리지 도메인 개발 완료 후) — 방금 COMPLETED로 판정된 신청 건에 대해
        //   프로그램에 연결된 mileagePolicy만큼 mileage_transaction을 적립해야 한다. 지금은 마일리지 도메인이
        //   개발 중이라 이 스케줄러에서 직접 연동하지 않는다.

        if (recruitingToOperating > 0 || operatingToClosed > 0 || completionJudged > 0) {
            log.info("프로그램 상태 자동 전환 - 모집중→운영중: {}건, 운영중→종료: {}건, 이수 판정: {}건",
                    recruitingToOperating, operatingToClosed, completionJudged);
        }
    }
}
