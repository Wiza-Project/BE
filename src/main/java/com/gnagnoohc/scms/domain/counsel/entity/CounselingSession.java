package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity @Getter
@Table(name = "counseling_session", uniqueConstraints = @UniqueConstraint(
        name = "uq_counseling_session_assignment_no", columnNames = {"counseling_assignment_id", "session_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingSession extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_session_id", nullable = false) private Integer counselingSessionId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_assignment_id", nullable = false) private CounselingAssignment counselingAssignment;
    @Column(name = "session_no", nullable = false) private Integer sessionNo;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at") private Instant endsAt;
    @Column(name = "attendance_status", nullable = false, length = 20) private String attendanceStatus = "SCHEDULED";
    @Column(name = "session_status", nullable = false, length = 20) private String sessionStatus = "PLANNED";
    @Column(name = "next_session_at") private Instant nextSessionAt;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
