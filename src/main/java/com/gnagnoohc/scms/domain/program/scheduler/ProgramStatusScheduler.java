package com.gnagnoohc.scms.domain.program.scheduler;

import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.event.ExtracurricularActivityCompletedEvent;
import com.gnagnoohc.scms.domain.program.event.ProgramCompletionJudgedEvent;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProgramStatusScheduler {

    private final ExtracurricularProgramRepository programRepository;
    private final ProgramApplicationRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 매분 정각에 실행. 모집중(DRAFT)→운영중(OPERATING)→종료(CLOSED)→이수 판정 순서로 처리해야
     * 스케줄러가 한동안 멈췄다 재개된 경우에도(여러 단계를 모두 지난 프로그램이 있는 경우) 같은 사이클
     * 안에서 최종 상태(이수 판정까지)를 한 번에 따라잡을 수 있다.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void transitionProgramStatuses() {
        Instant now = Instant.now();
        int recruitingToOperating = programRepository.transitionRecruitingToOperating(now);
        int operatingToClosed = programRepository.transitionOperatingToClosed(now);
        /**
         * 방금(또는 이전 사이클에) CLOSED로 전환됐지만 아직 이수 판정이 안 된 승인 건을 판정한다.
         * judgeCompletion 자체가 completion_status IS NULL 조건으로 멱등하므로 매분 호출해도 안전하다.
         * (이수번호(certificate_no) 채번도 이 안에서 함께 처리된다 — ProgramApplicationRepository.judgeCompletion 참고.)
         */
        int completionJudged = applicationRepository.judgeCompletion(now);
        /**
         * 방금 COMPLETED로 판정된 신청 건마다 ProgramCompletionJudgedEvent를 발행한다. 판정 + 이수증 발급은
         * judgeCompletion 안에서 이미 원자적으로 처리되므로 이 트랜잭션 커밋 시점에 확정된다. 마일리지 적립
         * 리스너는 AFTER_COMMIT + 자체 트랜잭션으로 분리돼 best-effort로 처리된다(적립 실패가 판정을 롤백하지
         * 않음, 누락분은 마일리지 스케줄러 배치가 회수). mileage 패키지를 직접 호출하지 않는 이유는
         * domain/program/package-info.java의 설계 메모(도메인 간 양방향 직접 의존 방지) 참고.
         */
        if (completionJudged > 0) {
            List<Integer> judgedApplicationIds = applicationRepository.findApplicationIdsJudgedCompletedAt(now);
            for (Integer applicationId : judgedApplicationIds) {
                eventPublisher.publishEvent(new ProgramCompletionJudgedEvent(applicationId));
            }
            /**
             * 취창업/이력서 도메인용 이벤트. ExtracurricularActivityCompletedEvent 참고 —
             * 구독 측은 @TransactionalEventListener(phase = AFTER_COMMIT)으로 받아야 한다.
             */
            for (ProgramApplication application :
                    applicationRepository.findWithProgramDetailsByApplicationIdIn(judgedApplicationIds)) {
                eventPublisher.publishEvent(ExtracurricularActivityCompletedEvent.from(application));
            }
        }

        if (recruitingToOperating > 0 || operatingToClosed > 0 || completionJudged > 0) {
            log.info("프로그램 상태 자동 전환 - 모집중→운영중: {}건, 운영중→종료: {}건, 이수 판정: {}건",
                    recruitingToOperating, operatingToClosed, completionJudged);
        }
    }
}
