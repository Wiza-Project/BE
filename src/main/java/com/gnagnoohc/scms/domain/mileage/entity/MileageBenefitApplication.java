package com.gnagnoohc.scms.domain.mileage.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** 학생이 인증서·장학금 등 마일리지 혜택을 신청한 이력을 관리한다. */
@Entity @Getter
@Table(name = "mileage_benefit_application", uniqueConstraints = @UniqueConstraint(
        name = "uq_mileage_benefit_application_policy_student", columnNames = {"benefit_policy_id", "student_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MileageBenefitApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "benefit_application_id", nullable = false) private Integer benefitApplicationId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "benefit_policy_id", nullable = false) private MileageBenefitPolicy benefitPolicy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AppUser student;
    @Column(name = "points_snapshot", nullable = false, precision = 10, scale = 2) private BigDecimal pointsSnapshot;
    @Column(name = "application_status", nullable = false, length = 20) private String applicationStatus = "APPLIED";
    @Column(name = "processed_by") private Integer processedBy;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "decision_reason", columnDefinition = "text") private String decisionReason;
    @Column(name = "applied_at", nullable = false) private Instant appliedAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    /** 학생이 장학금 신청을 제출한 시점의 점수를 고정해 신청 이력을 생성한다. */
    public static MileageBenefitApplication apply(
            MileageBenefitPolicy benefitPolicy,
            AppUser student,
            BigDecimal pointsSnapshot,
            Instant appliedAt
    ) {
        MileageBenefitApplication application = new MileageBenefitApplication();
        application.benefitPolicy = benefitPolicy;
        application.student = student;
        application.pointsSnapshot = pointsSnapshot;
        application.applicationStatus = "APPLIED";
        application.appliedAt = appliedAt;
        application.updatedAt = appliedAt;
        return application;
    }
}
