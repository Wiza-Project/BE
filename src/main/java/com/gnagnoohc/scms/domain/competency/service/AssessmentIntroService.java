package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentAttemptResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentIntroResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

// 동의(개인정보 처리방침 등) 자체는 공용 동의 모듈(domain/user/service/consent)이 담당한다 —
// 이 서비스는 ConsentVerifier로 "필수 동의를 다 마쳤는지"만 확인하고, attempt 생성·연결만 처리한다.
@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentIntroService {

    // 문항당 예상 응답 소요시간(초). 5점 리커트 자기보고식 문항 기준 통상치를 임시로 채택한 값으로,
    // schema/기획서에 별도 필드가 없어 BE에서 정한 추정값이다 — 실측 데이터가 쌓이면 조정한다.
    private static final int SECONDS_PER_QUESTION = 20;

    private static final String DUPLICATE_ATTEMPT_CONSTRAINT = "uq_assessment_attempt_round_student";

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentAttemptStartRecovery assessmentAttemptStartRecovery;
    private final AppUserRepository appUserRepository;
    private final ConsentVerifier consentVerifier;

    @Transactional(readOnly = true)
    public AssessmentIntroResponse getIntro(Integer roundId, Integer studentId) {
        AssessmentRound round = getRound(roundId);
        long questionCount = assessmentRoundQuestionRepository.countByAssessmentRound_AssessmentRoundId(roundId);
        AssessmentAttempt existing = assessmentAttemptRepository
                .findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(roundId, studentId)
                .orElse(null);

        return new AssessmentIntroResponse(
                round.getAssessmentRoundId(),
                round.getAssessmentName(),
                round.getStartsAt(),
                round.getEndsAt(),
                questionCount,
                estimatedMinutes(questionCount),
                existing == null ? null : existing.getAttemptId(),
                existing == null ? null : existing.getAttemptStatus()
        );
    }

    // 이미 응시를 시작한 학생이 다시 호출하면 새로 만들지 않고 기존 attempt를 그대로 반환한다(멱등).
    // AssessmentAttempt 유니크 제약(round+student)과 정합.
    public AssessmentAttemptResponse startAttempt(Integer roundId, Integer studentId) {
        AssessmentRound round = getRound(roundId);

        Optional<AssessmentAttempt> existing = assessmentAttemptRepository
                .findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(roundId, studentId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Instant now = Instant.now();
        if (now.isBefore(round.getStartsAt()) || now.isAfter(round.getEndsAt())) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
        }

        if (!consentVerifier.hasAgreedAllRequired(studentId, ConsentModuleCode.ASSESSMENT, now)) {
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }

        AppUser student = appUserRepository.getReferenceById(studentId);
        UserConsent linkedConsent = findRepresentativeConsent(studentId, now);

        AssessmentAttempt attempt;
        try {
            attempt = assessmentAttemptRepository.save(AssessmentAttempt.create(round, student, linkedConsent));
        } catch (DataIntegrityViolationException e) {
            attempt = recoverFromConcurrentStart(roundId, studentId, e);
        }

        return toResponse(attempt);
    }

    /**
     * 위 findBy~존재 확인과 save() 사이에 동시 요청(더블클릭 등)이 먼저 커밋되면 uq_assessment_attempt_round_student
     * 위반이 난다. 멱등 계약(이미 시작했으면 기존 attempt 반환)을 지키려면 여기서 CONFLICT를 던지고 클라이언트가
     * 재시도하게 하는 대신, 이긴 요청이 남긴 attempt를 바로 찾아 반환해야 한다. 다만 이 트랜잭션은 이미
     * aborted 상태라(Postgres) 같은 트랜잭션에서 재조회해도 실패하므로 별도 트랜잭션(REQUIRES_NEW)에 위임한다.
     */
    private AssessmentAttempt recoverFromConcurrentStart(Integer roundId, Integer studentId, DataIntegrityViolationException e) {
        String detail = e.getMostSpecificCause().getMessage();
        if (detail == null || !detail.contains(DUPLICATE_ATTEMPT_CONSTRAINT)) {
            throw e;
        }
        return assessmentAttemptStartRecovery.findExisting(roundId, studentId).orElseThrow(() -> e);
    }

    /**
     * attempt.userConsent는 증빙용 참조 하나만 가리킬 수 있어(FK 1개), 진단 응답에 가장 직결되는
     * 민감정보 처리 동의를 우선 연결하고 없으면 개인정보 수집·이용 동의로 대체한다. 게이트(필수 동의
     * 충족 여부)는 이미 hasAgreedAllRequired로 확인했으므로, 여기서 둘 다 못 찾아도 null로 두고 진행한다
     * (assessment_attempt.consent_id는 nullable).
     */
    private UserConsent findRepresentativeConsent(Integer studentId, Instant now) {
        return consentVerifier.findCurrentValidConsent(studentId, ConsentModuleCode.ASSESSMENT, ConsentType.SENSITIVE_INFO, now)
                .or(() -> consentVerifier.findCurrentValidConsent(studentId, ConsentModuleCode.ASSESSMENT, ConsentType.PERSONAL_INFO, now))
                .orElse(null);
    }

    private AssessmentRound getRound(Integer roundId) {
        return assessmentRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));
    }

    private long estimatedMinutes(long questionCount) {
        return (long) Math.ceil(questionCount * SECONDS_PER_QUESTION / 60.0);
    }

    private AssessmentAttemptResponse toResponse(AssessmentAttempt attempt) {
        return new AssessmentAttemptResponse(attempt.getAttemptId(), attempt.getAttemptStatus());
    }
}
