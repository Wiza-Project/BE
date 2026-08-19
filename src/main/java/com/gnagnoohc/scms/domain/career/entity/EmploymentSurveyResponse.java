package com.gnagnoohc.scms.domain.career.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@Table(name = "employment_survey_response")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmploymentSurveyResponse extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employment_survey_response_id", nullable = false)
    private Integer employmentSurveyResponseId;

    @Column(name = "survey_type", nullable = false, length = 30)
    private String surveyType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "respondent_user_id", nullable = true)
    private AppUser respondentUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_account_id", nullable = true)
    private CompanyAccount companyAccount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode responseData;

    @Column(name = "sent_at", nullable = true)
    private Instant sentAt;

    @Column(name = "responded_at", nullable = true)
    private Instant respondedAt;
}
