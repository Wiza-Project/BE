package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CompetencyDiagnosisMileageAccrualService.accrueAssessmentCompletion()은 동기 이벤트 리스너로 호출되어
 * 역량진단 제출 트랜잭션에 참여하므로, 동시 요청으로 인한 중복 적립을 예외로 던질 수 없다(제출 전체가 롤백됨).
 * 하지만 Postgres는 유니크 제약 위반으로 statement가 실패하면 트랜잭션 전체를 aborted 상태로 만들어서,
 * 같은 트랜잭션 안에서 예외를 잡고 정상 반환해도 커밋 시점에 실패한다(AssessmentAttemptStartRecovery와 동일한 제약).
 * 그래서 insert만 별도 트랜잭션(REQUIRES_NEW)으로 분리해 중복 제약 위반이 바깥 트랜잭션에 영향을 주지 않게 한다.
 *
 * <p>다만 이 REQUIRES_NEW 메서드 안에서 DataIntegrityViolationException을 잡아 정상 반환하면 안 된다 —
 * Postgres가 이미 이 트랜잭션을 aborted로 만들었기 때문에, Spring의 트랜잭션 AOP 인터셉터가 (예외 없이
 * 정상 반환됐다고 보고) rollback-only로 마킹된 이 트랜잭션을 커밋하려 시도하다 UnexpectedRollbackException을
 * 던진다. 그 예외는 여기서 잡을 수 없고 그대로 호출부의 호출부까지, 즉 바깥 트랜잭션
 * (AssessmentSubmissionService.submit())까지 전파되어 정상적인 제출 자체를 롤백시켜 버린다.
 * 그래서 이 클래스는 예외를 잡지 않고 그대로 던지며(인터셉터가 깨끗하게 롤백하도록), 중복 여부 판별과
 * 예외 처리는 이 REQUIRES_NEW 트랜잭션이 완전히 종료된 뒤인 호출부
 * {@link CompetencyDiagnosisMileageAccrualService#accrueAssessmentCompletion(Integer)}에서 한다 —
 * {@code ResumeExtracurricularActivityEventListener.handle()}이
 * {@code ResumeExtracurricularActivityUpsertService.save()}에 대해 하는 것과 같은 패턴이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentCompletionMileageAccrualWriter {

    /** 응시 회차당 1건만 적립되도록 보장하는 유니크 제약. 호출부가 중복 적립 예외를 판별할 때 쓴다. */
    public static final String DUPLICATE_ATTEMPT_ACCRUAL_CONSTRAINT =
            "uq_mileage_transaction_source_assessment_attempt";

    private final MileageTransactionRepository mileageTransactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insert(AssessmentAttempt attempt, MileagePolicy policy, BigDecimal grantablePoints, Integer attemptId) {
        mileageTransactionRepository.saveAndFlush(
                MileageTransaction.earnFromAssessmentCompletion(attempt, policy, grantablePoints, Instant.now()));
        return true;
    }
}
