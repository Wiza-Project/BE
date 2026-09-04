package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.error.DbConstraintViolationMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentCompletionMileageAccrualWriter {

    private static final String DUPLICATE_ATTEMPT_ACCRUAL_CONSTRAINT =
            "uq_mileage_transaction_source_assessment_attempt";

    private final MileageTransactionRepository mileageTransactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insert(AssessmentAttempt attempt, MileagePolicy policy, BigDecimal grantablePoints, Integer attemptId) {
        try {
            mileageTransactionRepository.saveAndFlush(
                    MileageTransaction.earnFromAssessmentCompletion(attempt, policy, grantablePoints, Instant.now()));
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (!DbConstraintViolationMatcher.contains(exception, DUPLICATE_ATTEMPT_ACCRUAL_CONSTRAINT)) {
                throw exception;
            }
            log.info("동시 요청으로 이미 적립된 역량진단 완료 건입니다. attemptId={}", attemptId);
            return false;
        }
    }
}
