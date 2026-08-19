package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity @Getter @Table(name = "counseling_schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingSchedule extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_schedule_id", nullable = false) private Integer counselingScheduleId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_type_id", nullable = false) private CounselingType counselingType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counselor_id", nullable = false) private AppUser counselor;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(name = "capacity", nullable = false) private Integer capacity = 1;
    @Column(name = "booking_deadline") private Instant bookingDeadline;
    @Column(name = "location", length = 300) private String location;
    @Column(name = "schedule_status", nullable = false, length = 20) private String scheduleStatus = "OPEN";
}
