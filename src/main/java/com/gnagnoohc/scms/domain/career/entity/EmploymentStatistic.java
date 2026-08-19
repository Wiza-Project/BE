package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.global.common.entity.CommonCode;
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

import java.math.BigDecimal;
import java.time.Instant;

/** This stores statistics imported after confirmation by an external institution. */
@Entity
@Getter
@Table(name = "employment_statistic", uniqueConstraints = @UniqueConstraint(
        name = "uq_employment_statistic_year_department_type",
        columnNames = {"academic_year", "department_code_id", "statistic_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmploymentStatistic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employment_statistic_id", nullable = false)
    private Integer employmentStatisticId;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_code_id", nullable = false)
    private CommonCode departmentCode;

    @Column(name = "statistic_type", nullable = false, length = 30)
    private String statisticType;

    @Column(name = "graduate_count", nullable = true)
    private Integer graduateCount;

    @Column(name = "employed_count", nullable = true)
    private Integer employedCount;

    @Column(name = "employment_rate", nullable = true, precision = 7, scale = 4)
    private BigDecimal employmentRate;

    @Column(name = "maintenance_rate", nullable = true, precision = 7, scale = 4)
    private BigDecimal maintenanceRate;

    @Column(name = "statistic_source", nullable = false, length = 100)
    private String statisticSource;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();
}
