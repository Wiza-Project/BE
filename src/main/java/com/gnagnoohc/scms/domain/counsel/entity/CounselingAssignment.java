package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
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
}
