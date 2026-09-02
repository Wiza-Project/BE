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

    /**
     * 자가 심리검사 결과를 저장할 때 쓰는 생성 지점을 한곳으로 모은다.
     * 문항별 원응답은 이 메서드의 인자로도 받지 않는다. 서비스가 채점을 마친 합계·수준·설명만 넘겨서,
     * 결과 엔티티 어디에도 원응답이 스쳐 지나갈 통로조차 만들지 않기 위해서다.
     * 자가검사는 상담 회기와 직접 연결되지 않으므로 counselingReservation은 항상 null이다.
     */
    public static PsychologicalTestResult createSelfTestResult(
            AppUser student,
            String testType,
            String testVersion,
            int totalScore,
            String resultLevel,
            String resultSummary,
            Instant testedAt
    ) {
        PsychologicalTestResult result = new PsychologicalTestResult();
        result.student = student;
        result.counselingReservation = null;
        result.testType = testType;
        result.testVersion = testVersion;
        result.totalScore = BigDecimal.valueOf(totalScore);
        result.resultLevel = resultLevel;
        result.resultSummary = resultSummary;
        result.testedAt = testedAt;
        return result;
    }
}
