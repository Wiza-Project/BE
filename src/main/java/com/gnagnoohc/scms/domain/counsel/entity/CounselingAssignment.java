package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 운영 DDL의 uq_counseling_assignment_active가 ended_at IS NULL인 활성 배정을 하나로 제한한다. */
@Entity @Getter @Table(name = "counseling_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_assignment_id", nullable = false) private Integer counselingAssignmentId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_reservation_id", nullable = false) private CounselingReservation counselingReservation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counselor_id", nullable = false) private AppUser counselor;
    @Column(name = "assigned_by", nullable = false) private Integer assignedBy;
    @Column(name = "assignment_reason", length = 500) private String assignmentReason;
    @Column(name = "assigned_at", nullable = false) private Instant assignedAt = Instant.now();
    @Column(name = "ended_at") private Instant endedAt;

    /**
     * 최초 배정(예약 승인 시점) 생성 규칙을 한곳에 둔다.
     * assignedAt을 엔티티 내부에서 새로 구하지 않고 서비스가 값으로 넘기는 이유는, 같은 트랜잭션에서
     * 함께 바뀌는 예약의 processedAt과 배정의 assignedAt이 서로 다른 시각으로 어긋나지 않게 하기 위해서다.
     * 최초 자동 배정은 상담사가 스스로 사유를 입력하지 않으므로 assignmentReason은 null을 허용한다.
     */
    public static CounselingAssignment create(
            CounselingReservation counselingReservation,
            AppUser counselor,
            Integer assignedBy,
            String assignmentReason,
            Instant now
    ) {
        CounselingAssignment assignment = new CounselingAssignment();
        assignment.counselingReservation = counselingReservation;
        assignment.counselor = counselor;
        assignment.assignedBy = assignedBy;
        assignment.assignmentReason = assignmentReason;
        assignment.assignedAt = now;
        return assignment;
    }

    /**
     * 예약 취소 등으로 활성 배정을 종료할 때 사용한다.
     * 이미 종료된 배정을 다시 종료하면 종료 시각이 덮어써지는 데이터 모순이 생기므로,
     * 그런 경우는 호출하는 쪽의 논리 오류로 보고 예외로 막는다.
     */
    public void end(Instant now) {
        if (this.endedAt != null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        this.endedAt = now;
    }

    /** 종료(endedAt) 처리되지 않은 배정인지 여부. 회기 생성·완료·취소는 활성 배정에서만 허용한다. */
    public boolean isActive() {
        return endedAt == null;
    }

    /** 회기·배정 API에서 요청자 본인이 이 배정의 담당 상담사인지 확인할 때 쓴다. */
    public boolean isOwnedBy(Integer counselorId) {
        return counselor.getUserId().equals(counselorId);
    }
}
