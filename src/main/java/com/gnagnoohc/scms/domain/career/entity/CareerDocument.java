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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "career_document", uniqueConstraints = @UniqueConstraint(
        name = "uq_career_document_student_type_version",
        columnNames = {"student_id", "document_type", "version_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareerDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "career_document_id", nullable = false)
    private Integer careerDocumentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_group_id", nullable = true)
    private FileGroup fileGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "document_title", nullable = false, length = 200)
    private String documentTitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_data", nullable = true, columnDefinition = "jsonb")
    private JsonNode contentData;

    @Column(name = "is_public", nullable = false)
    private boolean publicDocument = false;

    @Column(name = "ai_assistance_used", nullable = false)
    private boolean aiAssistanceUsed = false;
}
