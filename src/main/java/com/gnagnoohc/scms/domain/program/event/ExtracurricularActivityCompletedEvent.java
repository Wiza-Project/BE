package com.gnagnoohc.scms.domain.program.event;

import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;

import java.time.Instant;
import java.util.UUID;

/**
 * 비교과 프로그램 이수가 COMPLETED로 확정된 신청 건마다 발행되는, 취창업/이력서 도메인용 이벤트.
 * (주의) {@code ProgramCompletionJudgedEvent}와 달리 이 이벤트는 트랜잭션 커밋 이후 반영을 전제로 한다.
 * 발행 자체는 {@code ProgramStatusScheduler}의 {@code @Transactional} 메서드 안에서 동기 호출되지만,
 * 구독 측 리스너를 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}으로 등록하면 Spring이
 * 커밋 후에만 전달해준다 — 발행측 코드 변경 없이 이 요건을 만족한다.
 * 멱등 처리(같은 applicationId 재수신 시 upsert)는 구독 측(취창업 도메인) 책임이다.
 */
public record ExtracurricularActivityCompletedEvent(
        Integer studentId,
        Integer applicationId,
        Integer programId,
        String programName,
        String programTypeCode,
        String programTypeName,
        Integer competencyId,
        String competencyName,
        Instant activityStartedAt,
        Instant activityCompletedAt,
        String operatingDepartmentName,
        UUID eventId,
        Instant occurredAt
) {
    public static ExtracurricularActivityCompletedEvent from(ProgramApplication application) {
        var program = application.getProgram();
        return new ExtracurricularActivityCompletedEvent(
                application.getStudent().getUserId(),
                application.getApplicationId(),
                program.getProgramId(),
                program.getProgramName(),
                program.getProgramTypeCode().getCode(),
                program.getProgramTypeCode().getCodeName(),
                program.getCompetency().getCompetencyId(),
                program.getCompetency().getCompetencyName(),
                program.getOperationStartsAt(),
                program.getOperationEndsAt(),
                program.getOperatingUnitCode().getCodeName(),
                UUID.randomUUID(),
                Instant.now()
        );
    }
}
