package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 학생이 예약할 수 있는 상담 시간 구간과 현재 접수 상태를 보관한다.
 * 예약이 참조하는 자원이므로 수정과 마감은 서비스의 권한·예약·잠금 검사를 통과한 뒤에만 호출해야 한다.
 */
@Entity
@Getter
@Table(name = "counseling_schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingSchedule extends BaseTimeEntity {
    private static final String OPEN_STATUS = "OPEN";
    private static final String CLOSED_STATUS = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_schedule_id", nullable = false)
    private Integer counselingScheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counseling_type_id", nullable = false)
    private CounselingType counselingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counselor_id", nullable = false)
    private AppUser counselor;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "capacity", nullable = false)
    private Integer capacity = 1;

    @Column(name = "booking_deadline")
    private Instant bookingDeadline;

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "schedule_status", nullable = false, length = 20)
    private String scheduleStatus = OPEN_STATUS;

    /**
     * 새 일정이 항상 OPEN 상태로 시작하도록 생성 규칙을 한곳에 둔다.
     * 공개 생성자나 Setter를 열지 않아 상담사와 상태가 임의로 바뀌는 것을 막는다.
     */
    public static CounselingSchedule create(
            CounselingType counselingType,
            AppUser counselor,
            Instant startsAt,
            Instant endsAt,
            Integer capacity,
            Instant bookingDeadline,
            String location
    ) {
        CounselingSchedule schedule = new CounselingSchedule();
        schedule.counselingType = counselingType;
        schedule.counselor = counselor;
        schedule.startsAt = startsAt;
        schedule.endsAt = endsAt;
        schedule.capacity = capacity;
        schedule.bookingDeadline = bookingDeadline;
        schedule.location = location;
        schedule.scheduleStatus = OPEN_STATUS;
        return schedule;
    }

    /**
     * 상담사는 바꾸지 않고 수정이 허용된 일정 정보만 한 번에 교체한다.
     * 호출 전에 서비스가 본인 소유 OPEN 일정이며 예약 이력이 없는지 확인해야 한다.
     */
    public void update(
            CounselingType counselingType,
            Instant startsAt,
            Instant endsAt,
            Integer capacity,
            Instant bookingDeadline,
            String location
    ) {
        this.counselingType = counselingType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.bookingDeadline = bookingDeadline;
        this.location = location;
    }

    /**
     * OPEN 일정을 CLOSED로 바꿔 신규 예약만 차단한다.
     * 일정과 기존 예약을 삭제하지 않으므로 과거 예약 관계는 그대로 남는다.
     */
    public void close() {
        this.scheduleStatus = CLOSED_STATUS;
    }

    public boolean isOpen() {
        return OPEN_STATUS.equals(scheduleStatus);
    }

    public boolean isOwnedBy(Integer counselorId) {
        return counselor.getUserId().equals(counselorId);
    }
}
