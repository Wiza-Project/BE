package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.domain.program.event.ExtracurricularActivityCompletedEvent;
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

import java.time.Instant;
import java.util.UUID;

/**
 * 이력서 화면용 비교과 수료 이력 읽기 모델.
 *
 * <p>program 도메인이 이수를 확정할 때 발행하는 {@link ExtracurricularActivityCompletedEvent}를
 * 받아 적재하는 전용 테이블이다. {@code CareerDocument}(이력서 본문/버전)와는 분리돼 있고,
 * career 도메인은 이 테이블만 조회하며 program 테이블을 직접 조회하지 않는다.</p>
 *
 * <p>{@link ResumeCompetencySnapshot}(학생당 1행, 최신 결과로 계속 덮어씀)과 달리, 이 테이블은
 * 신청 건(application)당 1행이 쌓이는 이력 테이블이다({@code application_id} UNIQUE) — 한 학생이
 * 여러 프로그램을 이수하면 그만큼 행이 늘어나고, 이미 적재된 행은 갱신되지 않는다. 같은 신청 건이
 * 두 번 이수 확정되는 비즈니스 케이스가 없으므로(judgeCompletion이 completion_status IS NULL로
 * 멱등), 같은 applicationId의 재전달은 데이터가 동일한 단순 중복이라 재시도 없이 skip하면 충분하다.</p>
 */
@Entity
@Getter
@Table(name = "resume_extracurricular_activity", uniqueConstraints = @UniqueConstraint(
        name = "uq_resume_extracurricular_activity_application", columnNames = {"application_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeExtracurricularActivity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resume_extracurricular_activity_id", nullable = false)
    private Integer resumeExtracurricularActivityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "application_id", nullable = false)
    private Integer applicationId;

    @Column(name = "program_id", nullable = false)
    private Integer programId;

    @Column(name = "program_name", nullable = false, length = 200)
    private String programName;

    @Column(name = "program_type_code", nullable = false, length = 50)
    private String programTypeCode;

    @Column(name = "program_type_name", nullable = false, length = 100)
    private String programTypeName;

    @Column(name = "competency_id", nullable = false)
    private Integer competencyId;

    @Column(name = "competency_name", nullable = false, length = 100)
    private String competencyName;

    @Column(name = "operation_started_at", nullable = false)
    private Instant operationStartedAt;

    @Column(name = "operation_ended_at", nullable = false)
    private Instant operationEndedAt;

    @Column(name = "operating_department_name", nullable = false, length = 100)
    private String operatingDepartmentName;

    /** 이 행을 적재한 이벤트의 id/발생시각. 실이벤트/백필 모두 ExtracurricularActivityCompletedEvent.from()을 거치므로 항상 채워진다. */
    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "source_event_occurred_at")
    private Instant sourceEventOccurredAt;

    private ResumeExtracurricularActivity(AppUser student, ExtracurricularActivityCompletedEvent event) {
        this.student = student;
        this.applicationId = event.applicationId();
        this.programId = event.programId();
        this.programName = event.programName();
        this.programTypeCode = event.programTypeCode();
        this.programTypeName = event.programTypeName();
        this.competencyId = event.competencyId();
        this.competencyName = event.competencyName();
        this.operationStartedAt = event.activityStartedAt();
        this.operationEndedAt = event.activityCompletedAt();
        this.operatingDepartmentName = event.operatingDepartmentName();
        this.sourceEventId = event.eventId();
        this.sourceEventOccurredAt = event.occurredAt();
    }

    public static ResumeExtracurricularActivity from(AppUser student, ExtracurricularActivityCompletedEvent event) {
        return new ResumeExtracurricularActivity(student, event);
    }
}
