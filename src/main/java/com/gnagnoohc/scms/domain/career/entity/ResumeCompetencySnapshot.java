package com.gnagnoohc.scms.domain.career.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 이력서 화면용 핵심역량 진단 결과 스냅샷 (읽기 모델).
 *
 * <p>취창업 도메인이 핵심역량 도메인의 이벤트({@code AssessmentResultReadyEvent},
 * {@code AssessmentResultUnavailableEvent})를 받아 upsert하는 전용 테이블이다.
 * {@code CareerDocument}(이력서 본문/버전)와는 분리돼 있다 — 이력서 편집·버전 관리와
 * 자동연동 갱신이 서로의 데이터를 건드리지 않게 하기 위함이다.</p>
 *
 * <p>학생당 최신 결과 1행만 유지한다(student_id UNIQUE). 이력서 화면은 항상 "최신 연동
 * 결과"만 필요하고, 과거 진단 이력은 핵심역량 메뉴에 이미 별도로 존재한다.</p>
 */
@Entity
@Getter
@Table(name = "resume_competency_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uq_resume_competency_snapshot_student", columnNames = {"student_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeCompetencySnapshot extends BaseTimeEntity {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resume_competency_snapshot_id", nullable = false)
    private Integer resumeCompetencySnapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_id")
    private Integer attemptId;

    @Column(name = "assessment_round_id")
    private Integer assessmentRoundId;

    @Column(name = "assessment_name", length = 200)
    private String assessmentName;

    @Column(name = "academic_year")
    private Integer academicYear;

    @Column(name = "semester_code", length = 20)
    private String semesterCode;

    @Column(name = "semester_label", length = 50)
    private String semesterLabel;

    @Column(name = "assessment_phase", length = 20)
    private String assessmentPhase;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "overall_average_score", precision = 10, scale = 2)
    private BigDecimal overallAverageScore;

    /** 역량별 환산점수 스냅샷. {@code AssessmentResultReadyEvent.CompetencyScore} 목록을 그대로 JSON으로 담는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", columnDefinition = "jsonb")
    private JsonNode scores;

    /** UNAVAILABLE일 때만 채워짐 — 현재 유일값은 {@code AssessmentResultUnavailableEvent.REASON_NO_COMPLETED_ASSESSMENT}. */
    @Column(name = "unavailable_reason", length = 50)
    private String unavailableReason;

    /** 이 행을 만든 이벤트의 requestId. 제출·백필 기원 갱신은 null. */
    @Column(name = "last_request_id")
    private UUID lastRequestId;

    /** 이 행이 마지막으로 갱신된 시각 — READY는 진단 제출 시각, UNAVAILABLE은 이벤트 발생 시각. */
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    private ResumeCompetencySnapshot(AppUser student) {
        this.student = student;
        this.status = STATUS_UNAVAILABLE;
        this.syncedAt = Instant.now();
    }

    /** 학생의 스냅샷 행이 아직 없을 때 최초 생성한다. 상태는 리스너가 이어서 applyReady/applyUnavailable로 채운다. */
    public static ResumeCompetencySnapshot createFor(AppUser student) {
        return new ResumeCompetencySnapshot(student);
    }

    /** 완료 진단 결과로 갱신한다 (덮어쓰기). */
    public void applyReady(Integer attemptId, Integer assessmentRoundId, String assessmentName, Integer academicYear,
                            String semesterCode, String semesterLabel, String assessmentPhase, Instant submittedAt,
                            BigDecimal overallAverageScore, JsonNode scores, UUID requestId, Instant syncedAt) {
        this.status = STATUS_READY;
        this.attemptId = attemptId;
        this.assessmentRoundId = assessmentRoundId;
        this.assessmentName = assessmentName;
        this.academicYear = academicYear;
        this.semesterCode = semesterCode;
        this.semesterLabel = semesterLabel;
        this.assessmentPhase = assessmentPhase;
        this.submittedAt = submittedAt;
        this.overallAverageScore = overallAverageScore;
        this.scores = scores;
        this.unavailableReason = null;
        this.lastRequestId = requestId;
        this.syncedAt = syncedAt;
    }

    /** 완료 진단이 없다는 결과로 갱신한다. READY 상태를 이 메서드로 되돌리지 않는 건 호출부(리스너)의 책임이다. */
    public void applyUnavailable(String reason, UUID requestId, Instant syncedAt) {
        this.status = STATUS_UNAVAILABLE;
        this.attemptId = null;
        this.assessmentRoundId = null;
        this.assessmentName = null;
        this.academicYear = null;
        this.semesterCode = null;
        this.semesterLabel = null;
        this.assessmentPhase = null;
        this.submittedAt = null;
        this.overallAverageScore = null;
        this.scores = null;
        this.unavailableReason = reason;
        this.lastRequestId = requestId;
        this.syncedAt = syncedAt;
    }

    public boolean isReady() {
        return STATUS_READY.equals(status);
    }

    /** 이미 반영된 attemptId보다 같거나 오래된 이벤트면 true — 순서가 뒤바뀐 재전달로 보고 건너뛴다. */
    public boolean isStaleReadyEvent(Integer incomingAttemptId) {
        return isReady() && this.attemptId != null && this.attemptId >= incomingAttemptId;
    }
}
