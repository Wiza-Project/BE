package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselorCounselingPublicResultResponse;
import com.gnagnoohc.scms.domain.counsel.dto.StudentCounselingPublicResultResponse;
import com.gnagnoohc.scms.domain.counsel.dto.StudentCounselingPublicResultRow;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingPublicResult;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingAssignmentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingPrivateRecordRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingPublicResultRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingSessionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 회기별 공개 상담 결과(요약·실행 계획)의 조회·초안 저장·일반 공개와, 마지막 회기 결과를 이용한
 * 예약 최종 완료를 담당한다. 비공개 원문(CounselingPrivateRecordService)과는 별도 서비스로 분리해
 * 두 데이터가 같은 트랜잭션·DTO에 섞이지 않게 한다. 잠금 순서·상태표는 공개 상담 결과 설계 문서를
 * 그대로 따른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingPublicResultService {

    private static final String IN_PROGRESS_STATUS = "IN_PROGRESS";
    private static final String COMPLETED_STATUS = "COMPLETED";

    private final CounselUserRepository counselUserRepository;
    private final AppUserRepository appUserRepository;
    private final CounselingSessionRepository counselingSessionRepository;
    private final CounselingAssignmentRepository counselingAssignmentRepository;
    private final CounselingReservationRepository counselingReservationRepository;
    private final CounselingPrivateRecordRepository counselingPrivateRecordRepository;
    private final CounselingPublicResultRepository counselingPublicResultRepository;

    /**
     * 현재 담당자뿐 아니라 배정이 끝난 과거 담당자도 자신이 담당했던 회기라면 조회할 수 있다
     * (비공개 기록 조회와 같은 정책). 결과가 없으면 예외 없이 resultStatus=EMPTY로 응답한다.
     */
    public CounselorCounselingPublicResultResponse getResult(Integer sessionId, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        CounselingSession session = counselingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        CounselingAssignment assignment = session.getCounselingAssignment();
        if (!assignment.isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        CounselingReservation reservation = assignment.getCounselingReservation();
        CounselingPublicResult result = counselingPublicResultRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElse(null);
        return buildCounselorResponse(session, assignment, reservation, result, Instant.now());
    }

    /**
     * 초안 저장. 첫 저장은 새 행을 만들고, 이후 저장은 같은 행을 수정한다(versionNo=1 고정).
     * 공개된 결과의 수정은 CounselingPublicResult.updateDraft()가 S010으로 막는다.
     * 잠금 순서(회기 → 배정)와 "배정에 대한 첫 접근이 잠금 조회여야 한다"는 제약은
     * CounselingPrivateRecordService.saveDraft()와 동일한 이유(프록시 선초기화로 인한 무효 잠금 방지)다.
     */
    @Transactional
    public CounselorCounselingPublicResultResponse saveDraft(
            Integer sessionId, String resultSummary, String actionPlan, Integer counselorId
    ) {
        ensureActiveCounselor(counselorId);
        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        Integer assignmentId = session.getCounselingAssignment().getCounselingAssignmentId();
        CounselingAssignment assignment = counselingAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!assignment.isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        Instant now = Instant.now();
        if (!session.isPublicDraftAllowed(now)) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }

        CounselingPublicResult result = counselingPublicResultRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElse(null);
        if (result == null) {
            result = CounselingPublicResult.createDraft(session, resultSummary, actionPlan, counselorId);
            counselingPublicResultRepository.save(result);
        } else {
            result.updateDraft(resultSummary, actionPlan);
        }

        CounselingReservation reservation = assignment.getCounselingReservation();
        return buildCounselorResponse(session, assignment, reservation, result, now);
    }

    /**
     * 회기 결과 일반 공개. 예약 상태와 활성 배정은 변경하지 않는다(최종 완료와의 결정적 차이).
     * 공개 전에 같은 회기의 비공개 기록이 CONFIRMED인지만 확인하고 원문 자체는 조회하지 않는다.
     */
    @Transactional
    public CounselorCounselingPublicResultResponse publish(Integer sessionId, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        Integer assignmentId = session.getCounselingAssignment().getCounselingAssignmentId();
        CounselingAssignment assignment = counselingAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!assignment.isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        if (!session.isPublicPublishAllowed()) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        if (!isPrivateRecordConfirmed(sessionId)) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        CounselingPublicResult result = counselingPublicResultRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED));

        Instant now = Instant.now();
        result.publish(now);

        CounselingReservation reservation = assignment.getCounselingReservation();
        return buildCounselorResponse(session, assignment, reservation, result, now);
    }

    /**
     * 최종 완료. 잠금 순서는 반드시 회기 → 예약 → 배정이어야 한다(예약 취소의 "예약 → 배정 변경"
     * 순서와 맞춰 교착을 피하기 위함, 공개 상담 결과 설계 5.1). 회기에서 배정 ID만 얻고(프록시를
     * 초기화하지 않음), 잠금 없는 ID 전용 조회로 불변인 예약 ID를 구한 뒤 예약 → 배정 순서로 잠근다.
     * 모든 잠금을 잡은 뒤에야 전체 완료 조건을 다시 검증하고, 초안이면 공개까지 같은 트랜잭션에서 처리한다.
     */
    @Transactional
    public CounselorCounselingPublicResultResponse complete(Integer sessionId, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        Instant now = Instant.now();

        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        Integer assignmentId = session.getCounselingAssignment().getCounselingAssignmentId();

        Integer reservationId = counselingAssignmentRepository.findReservationIdByAssignmentId(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        CounselingReservation reservation = counselingReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        CounselingAssignment assignment = counselingAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        if (!assignment.isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        if (!IN_PROGRESS_STATUS.equals(reservation.getReservationStatus())) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        // 대상 회기가 "현재 활성 배정에서 가장 늦게 끝난 COMPLETED+PRESENT 회기"인지 확인한다.
        // findLatestCompletedPresentSessionId는 COMPLETED+PRESENT 회기만 대상으로 하므로,
        // 이 회기가 그 결과와 같다는 것만으로 회기 상태 조건까지 함께 검증된다.
        Integer latestSessionId = counselingSessionRepository
                .findLatestCompletedPresentSessionId(assignmentId)
                .orElse(null);
        if (!sessionId.equals(latestSessionId)) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        // 종료된 과거 배정의 회기까지 포함해 예약 전체에 PLANNED가 남아 있지 않아야 한다.
        if (counselingSessionRepository.existsPlannedSessionByReservationId(reservationId)) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        if (!isPrivateRecordConfirmed(sessionId)) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        CounselingPublicResult result = counselingPublicResultRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED));

        if (!result.isPublished()) {
            result.publish(now);
        }
        // 이미 PUBLISHED면 내용·publishedAt을 그대로 두고 예약 완료·배정 종료만 처리한다.

        reservation.complete(now);
        assignment.end(now);

        return buildCounselorResponse(session, assignment, reservation, result, now);
    }

    /** 학생 본인의 공개 결과 목록. 회기별 최신 공개 버전만, publishedAt DESC, publicResultId DESC로 반환한다. */
    public PageResponse<StudentCounselingPublicResultResponse> getStudentResults(
            Integer studentId, int page, int size
    ) {
        ensureActiveStudent(studentId);
        Page<StudentCounselingPublicResultRow> rows = counselingPublicResultRepository
                .findPublishedResultsForStudent(studentId, PageRequest.of(page, size));
        List<Integer> reservationIds = rows.getContent().stream()
                .map(StudentCounselingPublicResultRow::reservationId)
                .distinct()
                .toList();
        Map<Integer, Integer> finalSessionIdByReservation = finalSessionIdsByReservation(reservationIds);
        Map<Integer, String> statusByReservation = reservationStatusesByReservation(reservationIds);
        return PageResponse.from(rows.map(row -> StudentCounselingPublicResultResponse.from(
                row, isFinalResult(row, statusByReservation, finalSessionIdByReservation)
        )));
    }

    /** 학생 본인 공개 결과 상세. 다른 학생의 결과, 미공개 초안과 없는 결과는 모두 동일하게 S011이다. */
    public StudentCounselingPublicResultResponse getStudentResult(Integer sessionId, Integer studentId) {
        ensureActiveStudent(studentId);
        StudentCounselingPublicResultRow row = counselingPublicResultRepository
                .findPublishedResultForStudent(sessionId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PUBLIC_RESULT_NOT_FOUND));
        Integer reservationId = row.reservationId();
        Map<Integer, Integer> finalSessionIdByReservation = finalSessionIdsByReservation(List.of(reservationId));
        Map<Integer, String> statusByReservation = reservationStatusesByReservation(List.of(reservationId));
        return StudentCounselingPublicResultResponse.from(
                row, isFinalResult(row, statusByReservation, finalSessionIdByReservation)
        );
    }

    /**
     * 조회·저장·공개·완료가 공유하는 응답 조립이다. reservation·assignment는 상태 변경 후(complete)
     * 또는 변경 전(조회·저장·일반 공개) 시점의 현재 값을 그대로 반영해 계산한다.
     */
    private CounselorCounselingPublicResultResponse buildCounselorResponse(
            CounselingSession session,
            CounselingAssignment assignment,
            CounselingReservation reservation,
            CounselingPublicResult result,
            Instant now
    ) {
        Integer sessionId = session.getCounselingSessionId();
        Integer reservationId = reservation.getCounselingReservationId();
        Integer assignmentId = assignment.getCounselingAssignmentId();

        boolean privateConfirmed = isPrivateRecordConfirmed(sessionId);
        Integer finalSessionId = finalSessionIdsByReservation(List.of(reservationId)).get(reservationId);
        boolean finalResult = COMPLETED_STATUS.equals(reservation.getReservationStatus())
                && sessionId.equals(finalSessionId);

        boolean isLatestOfAssignment = assignment.isActive()
                && counselingSessionRepository.findLatestCompletedPresentSessionId(assignmentId)
                        .map(sessionId::equals)
                        .orElse(false);
        boolean noRemainingPlanned = !counselingSessionRepository.existsPlannedSessionByReservationId(reservationId);

        boolean canSaveDraft = assignment.isActive()
                && session.isPublicDraftAllowed(now)
                && (result == null || !result.isPublished());
        boolean canPublish = assignment.isActive()
                && session.isPublicPublishAllowed()
                && result != null
                && !result.isPublished()
                && privateConfirmed;
        boolean canCompleteReservation = assignment.isActive()
                && IN_PROGRESS_STATUS.equals(reservation.getReservationStatus())
                && isLatestOfAssignment
                && noRemainingPlanned
                && result != null
                && privateConfirmed;

        String createdByName = result == null
                ? null
                : appUserRepository.findById(result.getCreatedBy())
                        .map(AppUser::getUserName)
                        .orElse(null);

        return CounselorCounselingPublicResultResponse.from(
                sessionId, reservationId, assignmentId, result,
                createdByName, reservation.getReservationStatus(), assignment.isActive(),
                privateConfirmed, finalResult, canSaveDraft, canPublish, canCompleteReservation
        );
    }

    /**
     * 같은 회기의 비공개 기록이 CONFIRMED인지만 확인한다. 원문(privateContent)은 조회·전달하지 않는다.
     * 확정 여부만 필요하므로 엔티티 전체를 로드하지 않고 존재 여부 쿼리로 판정한다(원문 SELECT 회피).
     */
    private boolean isPrivateRecordConfirmed(Integer sessionId) {
        return counselingPrivateRecordRepository.existsConfirmedByCounselingSessionId(sessionId);
    }

    /**
     * 여러 예약의 마지막 COMPLETED+PRESENT 회기 ID를 배치로 계산한다. 후보를 한 번에 가져와
     * (endsAt desc, sessionId desc) 최댓값을 예약별로 골라낸다(N+1 방지).
     */
    private Map<Integer, Integer> finalSessionIdsByReservation(List<Integer> reservationIds) {
        if (reservationIds.isEmpty()) {
            return Map.of();
        }
        return counselingSessionRepository.findCompletedPresentSessionCandidates(reservationIds).stream()
                .collect(Collectors.groupingBy(
                        CounselingSessionRepository.CompletedPresentSessionCandidate::getReservationId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(
                                        Comparator.comparing(CounselingSessionRepository.CompletedPresentSessionCandidate::getEndsAt)
                                                .thenComparing(CounselingSessionRepository.CompletedPresentSessionCandidate::getSessionId)
                                ),
                                optional -> optional
                                        .map(CounselingSessionRepository.CompletedPresentSessionCandidate::getSessionId)
                                        .orElse(null)
                        )
                ));
    }

    /** finalResult 계산에 필요한 예약 상태를 배치로 조회한다(findAllById는 JpaRepository 기본 제공). */
    private Map<Integer, String> reservationStatusesByReservation(List<Integer> reservationIds) {
        if (reservationIds.isEmpty()) {
            return Map.of();
        }
        return counselingReservationRepository.findAllById(reservationIds).stream()
                .collect(Collectors.toMap(
                        CounselingReservation::getCounselingReservationId,
                        CounselingReservation::getReservationStatus
                ));
    }

    private boolean isFinalResult(
            StudentCounselingPublicResultRow row,
            Map<Integer, String> statusByReservation,
            Map<Integer, Integer> finalSessionIdByReservation
    ) {
        boolean completed = COMPLETED_STATUS.equals(statusByReservation.get(row.reservationId()));
        Integer finalSessionId = finalSessionIdByReservation.get(row.reservationId());
        return completed && row.sessionId().equals(finalSessionId);
    }

    private void ensureActiveCounselor(Integer counselorId) {
        if (!counselUserRepository.isActiveCounselor(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void ensureActiveStudent(Integer studentId) {
        if (!counselUserRepository.isActiveStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
