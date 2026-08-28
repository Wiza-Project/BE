package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 배정(CounselingAssignment)에 속한 개별 상담 회기다. 상태 전이(생성·완료·취소)는 반드시 이
 * 엔티티의 메서드를 통해서만 일어나며, 공개 Setter는 두지 않는다 — 트랜잭션마다 다른 곳에서
 * 상태를 직접 대입하면 설계 문서(consultation-session-management-design.md 3.3)의 상태표를
 * 어기는 코드가 생기기 쉽기 때문이다.
 */
@Entity @Getter
@Table(name = "counseling_session", uniqueConstraints = @UniqueConstraint(
        name = "uq_counseling_session_assignment_no", columnNames = {"counseling_assignment_id", "session_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingSession extends BaseTimeEntity {

    // 출석·회기 상태 문자열은 여기 상수를 통해서만 비교·대입한다(오타 방지, 상태 판정 로직 한곳 집중).
    private static final String ATTENDANCE_SCHEDULED = "SCHEDULED";
    private static final String ATTENDANCE_PRESENT = "PRESENT";
    private static final String ATTENDANCE_ABSENT = "ABSENT";
    private static final String ATTENDANCE_NO_SHOW = "NO_SHOW";
    private static final String SESSION_PLANNED = "PLANNED";
    private static final String SESSION_COMPLETED = "COMPLETED";
    private static final String SESSION_CANCELED = "CANCELED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_session_id", nullable = false) private Integer counselingSessionId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_assignment_id", nullable = false) private CounselingAssignment counselingAssignment;
    @Column(name = "session_no", nullable = false) private Integer sessionNo;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at") private Instant endsAt;
    @Column(name = "attendance_status", nullable = false, length = 20) private String attendanceStatus = ATTENDANCE_SCHEDULED;
    @Column(name = "session_status", nullable = false, length = 20) private String sessionStatus = SESSION_PLANNED;
    @Column(name = "next_session_at") private Instant nextSessionAt;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
    @Column(name = "created_by", nullable = false) private Integer createdBy;

    /**
     * 최초 회기와 후속 회기 생성이 공유하는 내부 규칙이다. startsAt<endsAt은 호출부(승인 트랜잭션,
     * 후속 회기 생성 서비스)가 이미 검증하지만, 엔티티 자신도 방어적으로 다시 확인한다.
     */
    private static CounselingSession create(
            CounselingAssignment assignment, int sessionNo, Instant startsAt, Instant endsAt, Integer createdBy
    ) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회기 시작 시각은 종료 시각보다 빨라야 합니다.");
        }
        CounselingSession session = new CounselingSession();
        session.counselingAssignment = assignment;
        session.sessionNo = sessionNo;
        session.startsAt = startsAt;
        session.endsAt = endsAt;
        session.attendanceStatus = ATTENDANCE_SCHEDULED;
        session.sessionStatus = SESSION_PLANNED;
        session.createdBy = createdBy;
        return session;
    }

    /** 예약 승인 트랜잭션에서만 호출한다. 승인된 CounselingSchedule의 시각을 그대로 복사해 sessionNo=1로 만든다. */
    public static CounselingSession createFirst(
            CounselingAssignment assignment, Instant startsAt, Instant endsAt, Integer createdBy
    ) {
        return create(assignment, 1, startsAt, endsAt, createdBy);
    }

    /** 활성 배정 잠금 뒤 MAX(sessionNo)+1로 채번한 번호를 서비스가 넘긴다. */
    public static CounselingSession createFollowUp(
            CounselingAssignment assignment, int sessionNo, Instant startsAt, Instant endsAt, Integer createdBy
    ) {
        return create(assignment, sessionNo, startsAt, endsAt, createdBy);
    }

    /**
     * 실제 상담 완료·출결 판정을 반영한다. PLANNED가 아니거나 아직 종료 시각이 지나지 않았으면
     * 필드를 하나도 바꾸지 않고 즉시 막아, 완료·취소된 회기의 재처리나 조기 완료를 차단한다.
     */
    public void complete(String attendanceStatus, Instant nextSessionAt, Instant now) {
        if (!SESSION_PLANNED.equals(sessionStatus)) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED);
        }
        if (endsAt == null || !now.isAfter(endsAt)) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED, "회기 종료 시각이 지난 뒤에만 완료할 수 있습니다.");
        }
        if (!ATTENDANCE_PRESENT.equals(attendanceStatus)
                && !ATTENDANCE_ABSENT.equals(attendanceStatus)
                && !ATTENDANCE_NO_SHOW.equals(attendanceStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출석 상태가 올바르지 않습니다.");
        }
        if (nextSessionAt != null && (!nextSessionAt.isAfter(now) || !nextSessionAt.isAfter(endsAt))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "다음 회기 예정 시각이 올바르지 않습니다.");
        }
        this.sessionStatus = SESSION_COMPLETED;
        this.attendanceStatus = attendanceStatus;
        this.nextSessionAt = nextSessionAt;
    }

    /**
     * 상담 시작 전 취소만 허용한다. 시작 시각이 지난 불참은 complete()의 ABSENT/NO_SHOW로 처리하는
     * 몫이므로 여기서는 취급하지 않는다. 취소해도 nextSessionAt은 남기지 않는다(설계 3.4).
     */
    public void cancel(String reason, Instant now) {
        if (!SESSION_PLANNED.equals(sessionStatus)) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED);
        }
        if (!now.isBefore(startsAt)) {
            throw new BusinessException(ErrorCode.SESSION_STATE_NOT_ALLOWED, "회기 시작 시각이 지나면 취소할 수 없습니다.");
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "취소 사유는 공백을 제외하고 1자 이상 500자 이하여야 합니다.");
        }
        this.sessionStatus = SESSION_CANCELED;
        this.nextSessionAt = null;
        this.cancellationReason = trimmed;
    }

    /** 응답의 canComplete 계산에 쓰는 파생 판정. now는 서비스가 값으로 넘긴다. */
    public boolean isCompletable(Instant now) {
        return SESSION_PLANNED.equals(sessionStatus) && endsAt != null && now.isAfter(endsAt);
    }

    /** 응답의 canCancel 계산에 쓰는 파생 판정. */
    public boolean isCancelable(Instant now) {
        return SESSION_PLANNED.equals(sessionStatus) && now.isBefore(startsAt);
    }

    /**
     * 비공개 기록 초안 작성이 허용되는 회기 상태다(설계: 시작 시각이 지난 PLANNED, 또는
     * 출석 확인된 COMPLETED). 배정 활성 여부·확정 여부는 여기서 다루지 않고 서비스가 별도로 확인한다.
     */
    public boolean isPrivateDraftAllowed(Instant now) {
        boolean startedPlanned = SESSION_PLANNED.equals(sessionStatus) && startsAt != null && !now.isBefore(startsAt);
        boolean completedPresent = SESSION_COMPLETED.equals(sessionStatus) && ATTENDANCE_PRESENT.equals(attendanceStatus);
        return startedPlanned || completedPresent;
    }

    /** 비공개 기록 확정이 허용되는 회기 상태다 — 출석 확인된 COMPLETED만 해당한다. */
    public boolean isPrivateConfirmAllowed(Instant now) {
        return SESSION_COMPLETED.equals(sessionStatus) && ATTENDANCE_PRESENT.equals(attendanceStatus);
    }

    /**
     * 공개 결과 초안 저장이 허용되는 회기 상태다(공개 상담 결과 설계 3.3). 시작 시각이 지난 PLANNED이거나
     * 출석 확인된 COMPLETED에서만 허용한다. 배정 활성 여부는 서비스가 별도로 확인한다.
     * isPrivateDraftAllowed와 조건식이 같아 보이지만, 공개 결과의 정책은 앞으로 비공개 기록과
     * 별개로 바뀔 수 있으므로 재사용하지 않고 전용 메서드로 분리해 둔다.
     */
    public boolean isPublicDraftAllowed(Instant now) {
        boolean startedPlanned = SESSION_PLANNED.equals(sessionStatus) && startsAt != null && !now.isBefore(startsAt);
        boolean completedPresent = SESSION_COMPLETED.equals(sessionStatus) && ATTENDANCE_PRESENT.equals(attendanceStatus);
        return startedPlanned || completedPresent;
    }

    /** 공개 결과 일반 공개가 허용되는 회기 상태다 — 출석 확인된 COMPLETED만 해당한다(설계 3.3). */
    public boolean isPublicPublishAllowed() {
        return SESSION_COMPLETED.equals(sessionStatus) && ATTENDANCE_PRESENT.equals(attendanceStatus);
    }
}
