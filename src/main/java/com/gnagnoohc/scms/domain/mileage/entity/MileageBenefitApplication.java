package com.gnagnoohc.scms.domain.mileage.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

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
}
