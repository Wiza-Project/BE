package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Table(name = "psychological_test_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PsychologicalTestResult extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "psychological_test_result_id", nullable = false)
    private Integer psychologicalTestResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counseling_reservation_id")
    private CounselingReservation counselingReservation;

    @Column(name = "test_type", nullable = false, length = 40)
    private String testType;

    @Column(name = "test_version", nullable = false, length = 30)
    private String testVersion;

    @Column(name = "total_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "result_level", nullable = false, length = 50)
    private String resultLevel;

    @Column(name = "result_summary", nullable = false, columnDefinition = "text")
    private String resultSummary;

    @Column(name = "tested_at", nullable = false)
    private Instant testedAt;
}
