package com.gnagnoohc.scms.domain.career.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Table(name = "employment_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmploymentRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employment_record_id", nullable = false)
    private Integer employmentRecordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_group_id", nullable = true)
    private FileGroup fileGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_account_id", nullable = true)
    private CompanyAccount companyAccount;

    @Column(name = "entered_by", nullable = true)
    private Integer enteredBy;

    @Column(name = "verified_by", nullable = true)
    private Integer verifiedBy;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "employment_start_date", nullable = false)
    private LocalDate employmentStartDate;

    @Column(name = "employment_type", nullable = true, length = 30)
    private String employmentType;

    @Column(name = "evidence_type", nullable = true, length = 30)
    private String evidenceType;

    @Column(name = "employment_status", nullable = false, length = 20)
    private String employmentStatus = "REPORTED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "employment_detail", nullable = true, columnDefinition = "jsonb")
    private JsonNode employmentDetail;

    @Column(name = "verified_at", nullable = true)
    private Instant verifiedAt;

    @Column(name = "month_3_status", nullable = true, length = 30)
    private String month3Status;

    @Column(name = "month_6_status", nullable = true, length = 30)
    private String month6Status;

    @Column(name = "month_11_status", nullable = true, length = 30)
    private String month11Status;
}
