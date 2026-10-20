package com.gnagnoohc.scms.domain.program.entity;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Getter
@Table(name = "extracurricular_program", uniqueConstraints = @UniqueConstraint(
        name = "uq_extracurricular_program_file_group_id",
        columnNames = "file_group_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtracurricularProgram extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "program_id", nullable = false) private Integer programId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_group_id") private FileGroup fileGroup;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "operating_unit_code_id", nullable = false) private CommonCode operatingUnitCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "program_type_code_id", nullable = false) private CommonCode programTypeCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competency_id", nullable = false) private Competency competency;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mileage_policy_id") private MileagePolicy mileagePolicy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "manager_user_id", nullable = false) private AppUser managerUser;
    @Column(name = "program_name", nullable = false, length = 200) private String programName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "recruitment_starts_at", nullable = false) private Instant recruitmentStartsAt;
    @Column(name = "recruitment_ends_at", nullable = false) private Instant recruitmentEndsAt;
    @Column(name = "operation_starts_at", nullable = false) private Instant operationStartsAt;
    @Column(name = "operation_ends_at", nullable = false) private Instant operationEndsAt;
    @Column(name = "capacity", nullable = false) private Integer capacity;
    @Column(name = "completion_rate", nullable = false, precision = 5, scale = 2) private BigDecimal completionRate = new BigDecimal("80");
    @Enumerated(EnumType.STRING)
    @Column(name = "program_status", nullable = false, length = 20) private ProgramStatus programStatus = ProgramStatus.DRAFT;
    @Column(name = "updated_by") private Integer updatedBy;
}
