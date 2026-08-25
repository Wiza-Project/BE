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
    // reservationStatus 비교·변경에 쓰는 상태 문자열 상수.
    // "REQUESTED" 같은 리터럴을 메서드마다 직접 쓰면 오타가 나도 컴파일러가 잡아주지 못하므로,
    // 상태를 다루는 곳에서는 항상 이 상수를 통해서만 비교·대입한다.
    private static final String REQUESTED_STATUS = "REQUESTED";
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String REJECTED_STATUS = "REJECTED";
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
     * now를 엔티티 내부에서 Instant.now()로 새로 구하지 않고 서비스가 값으로 넘겨주는 이유는,
     * 같은 요청 안의 여러 검사가 서로 다른 시각을 기준으로 판단하지 않게 하고, 테스트에서 임의의
     * 시각을 주입해 "마감 직전/직후" 같은 경계 상황을 재현할 수 있게 하기 위해서다.
     * 두 조건 중 하나라도 어기면 즉시 예외를 던지고 필드는 하나도 바뀌지 않으므로,
     * 취소 처리가 절반만 반영된 상태(상태만 바뀌고 사유는 안 남는 등)는 생기지 않는다.
     */
    // APPROVED 상태의 예약을 취소하면 CounselingReservationService.cancel()이 같은 트랜잭션 안에서
    // 이 예약의 활성 배정(CounselingAssignment)도 함께 종료(ended_at 세팅)한다. 여기 cancel()은
    // 예약 자체의 상태·사유만 책임지고, 배정 종료는 서비스가 담당한다(엔티티는 다른 엔티티를 몰라야 함).
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
     * 상담사가 REQUESTED 상태 예약을 승인한다.
     * 이미 처리(승인·거절)됐거나 취소된 예약을 다시 승인하면 배정이 중복 생성되거나 상태가
     * 되돌아갈 수 있으므로, REQUESTED가 아니면 필드를 하나도 바꾸지 않고 즉시 예외로 막는다.
     * 같은 예약에 동시에 승인 요청이 두 번 들어와도, 서비스가 미리 잡은 행 잠금 덕분에 두 번째
     * 요청은 이미 APPROVED로 바뀐 상태를 보고 여기서 걸린다.
     */
    public void approve(Integer processedBy, Instant now) {
        if (!REQUESTED_STATUS.equals(reservationStatus)) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_RESERVATION);
        }
        this.reservationStatus = APPROVED_STATUS;
        this.processedBy = processedBy;
        this.processedAt = now;
    }

    /**
     * 상담사가 REQUESTED 상태 예약을 거절한다. approve()와 같은 이유로 REQUESTED가 아니면 막는다.
     * 거절 사유는 학생에게 공개되는 처리 결과이므로 반드시 함께 저장한다.
     */
    public void reject(String decisionReason, Integer processedBy, Instant now) {
        if (!REQUESTED_STATUS.equals(reservationStatus)) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_RESERVATION);
        }
        this.reservationStatus = REJECTED_STATUS;
        this.processedBy = processedBy;
        this.processedAt = now;
        this.decisionReason = decisionReason;
    }

    /**
     * 취소 처리 전에 "취소되기 직전까지 APPROVED였는지"를 판별하는 데 쓴다.
     * cancel()이 상태를 CANCELED로 바꾸고 나면 더 이상 구분할 수 없으므로 반드시 cancel() 호출 전에 확인한다.
     */
    public boolean isApproved() {
        return APPROVED_STATUS.equals(reservationStatus);
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
