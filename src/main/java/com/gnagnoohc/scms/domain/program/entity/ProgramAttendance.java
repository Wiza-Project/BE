package com.gnagnoohc.scms.domain.program.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity @Getter
@Table(name = "program_attendance", uniqueConstraints = @UniqueConstraint(
        name = "uq_program_attendance_application_session", columnNames = {"application_id", "program_session_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramAttendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id", nullable = false) private Integer attendanceId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id", nullable = false) private ProgramApplication application;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "program_session_id", nullable = false) private ProgramSession programSession;
    @Column(name = "attendance_status", nullable = false, length = 20) private String attendanceStatus;
    @Column(name = "attended_minutes") private Integer attendedMinutes;
    @Column(name = "note", length = 500) private String note;
    @Column(name = "recorded_by") private Integer recordedBy;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt = Instant.now();
}
