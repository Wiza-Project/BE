package com.gnagnoohc.scms.domain.counsel.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity @Getter @Table(name = "counseling_reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingReservation extends BaseTimeEntity {
    private static final String REQUESTED_STATUS = "REQUESTED";
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String CANCELED_STATUS = "CANCELED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_reservation_id", nullable = false) private Integer counselingReservationId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_type_id", nullable = false) private CounselingType counselingType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_schedule_id") private CounselingSchedule counselingSchedule;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AppUser student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_consent_id") private UserConsent userConsent;
    @Column(name = "request_content", nullable = false, columnDefinition = "text") private String requestContent;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "intake_data", columnDefinition = "jsonb") private JsonNode intakeData;
    @Column(name = "reservation_status", nullable = false, length = 20) private String reservationStatus = "REQUESTED";
    @Column(name = "processed_by") private Integer processedBy;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "decision_reason", columnDefinition = "text") private String decisionReason;
    @Column(name = "canceled_at") private Instant canceledAt;
    @Column(name = "cancellation_reason", columnDefinition = "text") private String cancellationReason;
    @Column(name = "change_reason", columnDefinition = "text") private String changeReason;

    /**
     * 예약 생성 시점의 학생, 유형, 동의 이력과 선택 일정만 고정한다.
     * 승인·배정은 별도 유스케이스이므로 여기서 처리 상태를 바꾸지 않는다.
     */
    public static CounselingReservation create(
            CounselingType counselingType,
            CounselingSchedule counselingSchedule,
            AppUser student,
            UserConsent userConsent,
            String requestContent
    ) {
        CounselingReservation reservation = new CounselingReservation();
        reservation.counselingType = counselingType;
        reservation.counselingSchedule = counselingSchedule;
        reservation.student = student;
        reservation.userConsent = userConsent;
        reservation.requestContent = requestContent;
        reservation.reservationStatus = "REQUESTED";
        return reservation;
    }

    /**
     * 학생이 직접 취소 가능한 상태(REQUESTED, APPROVED)와 일정 마감 전인지 여기서 함께 확인한다.
     * 진행중·완료·거절·이미 취소된 예약은 취소 대상이 아니므로 서비스가 아니라 엔티티가 한 곳에서 막는다.
     */
    // ponytail: BLOCKED(체크리스트 6 의존) — APPROVED 취소 시 활성 배정 종료 미처리.
    //   현재 승인·배정(6번) 미구현이라 APPROVED 도달 경로가 없어 잠복. 6번 구현 시
    //   이 취소 트랜잭션에서 CounselingAssignment.ended_at을 함께 채워 활성 배정 모순을 막을 것.
    public void cancel(String reason, Instant now) {
        if (!REQUESTED_STATUS.equals(reservationStatus) && !APPROVED_STATUS.equals(reservationStatus)) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_CONFIRMED);
        }
        if (!isBeforeDeadline(now)) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_CONFIRMED, "취소 가능한 기한이 지난 예약입니다.");
        }
        this.reservationStatus = CANCELED_STATUS;
        this.canceledAt = now;
        this.cancellationReason = reason;
    }

    /**
     * 아직 상담사 승인 전(REQUESTED)인 예약만 다른 일정으로 재배정한다.
     * 같은 예약 행의 일정 FK만 교체하고 새로 만들지 않으며, 새 일정 자체의 마감·정원·중복 검증은
     * 서비스가 잠금을 잡은 뒤 별도로 수행한다(엔티티는 상태·기한만 책임진다).
     */
    public void changeSchedule(CounselingSchedule newSchedule, String reason, Instant now) {
        ensureChangeable(now);
        this.counselingSchedule = newSchedule;
        this.changeReason = reason;
    }

    /**
     * 새 일정 잠금·재검증 전에 먼저 호출해, 이미 변경 대상이 아닌 예약이면 불필요한 일정 잠금을 걸지 않게 한다.
     * changeSchedule()도 실제 교체 직전 같은 검사를 다시 거쳐 검증과 반영이 항상 함께 지켜지도록 한다.
     */
    public void ensureChangeable(Instant now) {
        if (!REQUESTED_STATUS.equals(reservationStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 수 없는 상태이거나 기한이 지난 예약입니다.");
        }
        if (!isBeforeDeadline(now)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 수 없는 상태이거나 기한이 지난 예약입니다.");
        }
    }

    /**
     * 예약이 참조하는 현재 일정의 bookingDeadline 전까지만 허용하고, 마감이 없으면 상담 시작 전까지 허용한다.
     * 참조 일정이 없으면(현재 신청 경로상 발생하지 않음) 기한을 판단할 수 없으므로 안전하게 거부한다.
     */
    private boolean isBeforeDeadline(Instant now) {
        if (counselingSchedule == null) {
            return false;
        }
        Instant deadline = counselingSchedule.getBookingDeadline() != null
                ? counselingSchedule.getBookingDeadline()
                : counselingSchedule.getStartsAt();
        return deadline.isAfter(now);
    }
}
