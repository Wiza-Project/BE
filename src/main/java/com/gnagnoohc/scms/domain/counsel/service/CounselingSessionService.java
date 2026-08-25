package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionRow;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingAssignmentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingScheduleRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingSessionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * 상담 회기 조회·생성·완료·취소를 담당한다. 트랜잭션 경계는 설계 문서
 * (consultation-session-management-design.md 4장)의 후속 회기 생성/완료/취소 세 절을 그대로 따른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingSessionService {

    private static final Set<String> VALID_SESSION_STATUSES = Set.of("PLANNED", "COMPLETED", "CANCELED");

    private final CounselUserRepository counselUserRepository;
    private final CounselingAssignmentRepository counselingAssignmentRepository;
    private final CounselingSessionRepository counselingSessionRepository;
    private final CounselingScheduleRepository counselingScheduleRepository;
    private final CounselingReservationRepository counselingReservationRepository;

    public PageResponse<CounselingSessionResponse> getSessions(
            Integer counselorId, int page, int size, String sessionStatus, Instant from, Instant to
    ) {
        ensureActiveCounselor(counselorId);
        if (sessionStatus != null && !VALID_SESSION_STATUSES.contains(sessionStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sessionStatus 값이 올바르지 않습니다.");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from은 to보다 이전이어야 합니다.");
        }
        Instant now = Instant.now();
        return PageResponse.from(counselingSessionRepository
                .findSessions(counselorId, sessionStatus, from, to, PageRequest.of(page, size))
                .map(row -> CounselingSessionResponse.from(row, counselorId, now)));
    }

    public CounselingSessionResponse getSession(Integer sessionId, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        CounselingSessionRow row = counselingSessionRepository.findDetailRow(sessionId, counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return CounselingSessionResponse.from(row, counselorId, Instant.now());
    }

    /**
     * 트랜잭션 경계(설계 4.2): 상담사 사용자 행 -> 활성 배정 행 순서로 잠근 뒤 시간·상호 중복을
     * 확인하고 MAX(sessionNo)+1로 채번해 저장한다. 일정 등록·수정도 같은 상담사 사용자 행을 먼저
     * 잠그므로 이 트랜잭션과 직렬화된다.
     */
    @Transactional
    public CounselingSessionResponse createFollowUp(
            Integer assignmentId, Instant startsAt, Instant endsAt, Integer counselorId
    ) {
        ensureActiveCounselor(counselorId);
        Instant now = Instant.now();
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회기 시작 시각은 종료 시각보다 빨라야 합니다.");
        }
        // 미래 시각의 후속 회기 생성은 API 계약(consultation-domain-api.md 오류표)상 S008(허용되지 않은 상태)이다.
        // 잘못된 시간 범위(startsAt>=endsAt, startsAt<assignedAt)의 C001과 구분한다.
        if (startsAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED, "미래 시각의 회기는 생성할 수 없습니다.");
        }

        // 같은 상담사의 빈 시간대를 동시에 선점하지 못하도록 사용자 행부터 잠근다(일정 등록·수정과 같은 순서).
        // 잠근 뒤에는 시작부의 비잠금 검사(ensureActiveCounselor) 대신 잠긴 행에서 활성·ST200을 다시 확인해,
        // 검사와 잠금 사이에 계정 비활성화·역할 회수가 커밋된 경우를 배제한다(CounselingScheduleService와 같은 패턴).
        AppUser lockedCounselor = counselUserRepository.findByIdForUpdate(counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (!"ACTIVE".equals(lockedCounselor.getAccountStatus())
                || !counselUserRepository.hasCounselorRole(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        CounselingAssignment assignment = counselingAssignmentRepository.findByIdForUpdate(assignmentId)
                .filter(a -> a.isOwnedBy(counselorId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED, "종료된 배정에는 회기를 생성할 수 없습니다.");
        }
        if (startsAt.isBefore(assignment.getAssignedAt())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회기 시작 시각은 배정 시각 이후여야 합니다.");
        }

        boolean overlapsSchedule = counselingScheduleRepository
                .existsOverlappingSchedule(counselorId, startsAt, endsAt);
        boolean overlapsSession = counselingSessionRepository
                .existsOverlappingSessionForCounselor(counselorId, startsAt, endsAt);
        if (overlapsSchedule || overlapsSession) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }

        int nextSessionNo = counselingSessionRepository.findMaxSessionNo(assignmentId).orElse(0) + 1;
        CounselingSession session = CounselingSession.createFollowUp(
                assignment, nextSessionNo, startsAt, endsAt, counselorId
        );
        counselingSessionRepository.save(session);

        CounselingSessionRow row = counselingSessionRepository
                .findDetailRow(session.getCounselingSessionId(), counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return CounselingSessionResponse.from(row, counselorId, now);
    }

    /**
     * 트랜잭션 경계(설계 4.3): PLANNED 확인, 출결·회기 상태·nextSessionAt 반영, PRESENT일 때만
     * APPROVED 예약을 IN_PROGRESS로 바꾸는 것까지 한 트랜잭션이다.
     */
    @Transactional
    public CounselingSessionResponse complete(
            Integer sessionId, String attendanceStatus, Instant nextSessionAt, Integer counselorId
    ) {
        ensureActiveCounselor(counselorId);
        Instant now = Instant.now();
        CounselingSession session = getOwnedSessionForUpdate(sessionId, counselorId);
        CounselingAssignment assignment = session.getCounselingAssignment();
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED, "종료된 배정의 회기는 변경할 수 없습니다.");
        }

        session.complete(attendanceStatus, nextSessionAt, now);
        if ("PRESENT".equals(attendanceStatus)) {
            // 예약 상태를 바꾸기 전에 예약 행을 잠그고 최신 상태를 다시 읽어 전이한다. 학생 취소도 같은
            // 예약 행을 잠그므로(findByIdForUpdate) 두 트랜잭션이 직렬화된다. 잠금 없이 LAZY 로드한 예약을
            // 그대로 수정하면 이 트랜잭션의 flush가 @Version 없는 전체 컬럼 UPDATE로 나가, 먼저 커밋된 취소를
            // 덮어써 취소된 예약이 IN_PROGRESS로 되살아나고 취소 사유가 사라진다(lost update).
            // markInProgressIfApproved()는 APPROVED일 때만 전이하므로, 사이에 취소가 커밋됐으면 no-op이 되어 취소가 보존된다.
            Integer reservationId = assignment.getCounselingReservation().getCounselingReservationId();
            CounselingReservation reservation = counselingReservationRepository.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
            reservation.markInProgressIfApproved();
        }

        CounselingSessionRow row = counselingSessionRepository
                .findDetailRow(sessionId, counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return CounselingSessionResponse.from(row, counselorId, now);
    }

    /**
     * 트랜잭션 경계(설계 4.4): PLANNED 확인, CANCELED 전환과 취소 사유 저장만 한 트랜잭션에서 처리한다.
     * 예약·배정은 건드리지 않는다.
     */
    @Transactional
    public CounselingSessionResponse cancel(Integer sessionId, String cancellationReason, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        Instant now = Instant.now();
        CounselingSession session = getOwnedSessionForUpdate(sessionId, counselorId);
        CounselingAssignment assignment = session.getCounselingAssignment();
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED, "종료된 배정의 회기는 변경할 수 없습니다.");
        }

        session.cancel(cancellationReason, now);

        CounselingSessionRow row = counselingSessionRepository
                .findDetailRow(sessionId, counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return CounselingSessionResponse.from(row, counselorId, now);
    }

    /**
     * 완료·취소가 공유하는 잠금·소유권 확인이다. 회기 자체를 잠근 뒤 배정의 담당 상담사가
     * 요청자 본인인지 확인하고, 아니면(또는 회기가 없으면) 존재를 노출하지 않도록 S007로 통일한다.
     */
    private CounselingSession getOwnedSessionForUpdate(Integer sessionId, Integer counselorId) {
        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getCounselingAssignment().isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private void ensureActiveCounselor(Integer counselorId) {
        if (!counselUserRepository.isActiveCounselor(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
