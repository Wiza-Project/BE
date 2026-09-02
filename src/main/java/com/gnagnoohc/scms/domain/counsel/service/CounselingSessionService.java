package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingSessionResponse;
import com.gnagnoohc.scms.domain.counsel.dto.projection.CounselingSessionRow;
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
    private final CounselManagementAccessPolicy counselManagementAccessPolicy;

    /**
     * ST200+ST300 상담사는 본인의 CS200 회기만 봐야 하므로, 조회 조건 자체에 careerOnly를 넘겨
     * 다른 유형의 회기 행을 애초에 읽지 않는다.
     */
    public PageResponse<CounselingSessionResponse> getSessions(
            Integer counselorId, int page, int size, String sessionStatus, Instant from, Instant to
    ) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        boolean careerOnly = scope == CounselManagementAccessPolicy.Scope.CAREER_ONLY;
        if (sessionStatus != null && !VALID_SESSION_STATUSES.contains(sessionStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sessionStatus 값이 올바르지 않습니다.");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from은 to보다 이전이어야 합니다.");
        }
        Instant now = Instant.now();
        return PageResponse.from(counselingSessionRepository
                .findSessions(counselorId, careerOnly, sessionStatus, from, to, PageRequest.of(page, size))
                .map(row -> CounselingSessionResponse.from(row, counselorId, now)));
    }

    /**
     * 상세 조회는 소유권(findDetailRow의 counselorId 조건)을 통과한 뒤 유형 범위까지 정책으로
     * 검사한다. 다른 유형이면 소유하지 않은 회기와 동일하게 S007로 통일해 존재를 노출하지 않는다.
     */
    public CounselingSessionResponse getSession(Integer sessionId, Integer counselorId) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        CounselingSessionRow row = counselingSessionRepository.findDetailRow(sessionId, counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        ensureTypeInScope(scope, row.counselingTypeCode());
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
        // 잠근 뒤 정책이 활성·STAFF·ST200을 다시 확인해, 이 요청 시작 전에 이미 커밋된 계정 비활성화·
        // 역할 회수는 놓치지 않는다(CounselingScheduleService와 같은 패턴). 다만 UserRole 행 자체는
        // 잠그지 않으므로 이 확인 직후 동시에 역할이 회수·커밋되는 경쟁까지 막지는 못한다(동시 역할
        // 변경 프로토콜은 범위 밖). 기존 인라인 활성·역할 검사를 복제하지 않고 정책 호출로 교체했다.
        AppUser lockedCounselor = counselUserRepository.findByIdForUpdate(counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(lockedCounselor);

        CounselingAssignment assignment = counselingAssignmentRepository.findByIdForUpdate(assignmentId)
                .filter(a -> a.isOwnedBy(counselorId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        // 부작용(겹침 조회·회기 저장)보다 먼저 유형 범위를 확인한다. 여기서 걸리면 아무 것도 바뀌지 않는다.
        ensureTypeInScope(scope, assignment.getCounselingReservation().getCounselingType().getTypeCode());
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
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        Instant now = Instant.now();
        CounselingSession session = getOwnedSessionForUpdate(sessionId, counselorId);
        CounselingAssignment assignment = session.getCounselingAssignment();
        // 회기 상태를 바꾸거나 예약을 IN_PROGRESS로 넘기기 전에 유형 범위부터 확인한다.
        ensureTypeInScope(scope, assignment.getCounselingReservation().getCounselingType().getTypeCode());
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
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        Instant now = Instant.now();
        CounselingSession session = getOwnedSessionForUpdate(sessionId, counselorId);
        CounselingAssignment assignment = session.getCounselingAssignment();
        // 회기를 CANCELED로 바꾸기 전에 유형 범위부터 확인한다.
        ensureTypeInScope(scope, assignment.getCounselingReservation().getCounselingType().getTypeCode());
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

    /**
     * 회기는 항상 DIRECT 유형 일정에서만 만들어지므로(일정 등록 자체가 DIRECT만 허용) 신청 경로는
     * 고정값으로 넘긴다. 다른 유형 회기임을 그대로 노출하면 "내 담당 회기인데 유형이 안 맞는다"는
     * 사실이 드러나므로, 소유권 실패와 동일하게 SESSION_NOT_FOUND(S007)로 응답을 통일한다.
     */
    private void ensureTypeInScope(CounselManagementAccessPolicy.Scope scope, String typeCode) {
        if (!counselManagementAccessPolicy.allows(scope, typeCode, "DIRECT")) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
    }
}
