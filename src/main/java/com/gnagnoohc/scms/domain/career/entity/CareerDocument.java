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

    public static final String TYPE_COVER_LETTER = "COVER_LETTER";
    public static final String TYPE_PORTFOLIO = "PORTFOLIO";
    public static final String TYPE_RESUME = "RESUME";

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

    private CareerDocument(AppUser student, FileGroup fileGroup, String documentType, Integer versionNo,
                            String documentTitle, JsonNode contentData, boolean aiAssistanceUsed) {
        this.student = student;
        this.fileGroup = fileGroup;
        this.documentType = documentType;
        this.versionNo = versionNo;
        this.documentTitle = documentTitle;
        this.contentData = contentData;
        this.aiAssistanceUsed = aiAssistanceUsed;
    }

    /** 자기소개서 신규 작성. versionNo는 학생·유형 기준 다음 버전 번호를 호출부에서 계산해 전달한다. */
    public static CareerDocument createCoverLetter(AppUser student, Integer versionNo, String documentTitle,
                                                     JsonNode contentData, boolean aiAssistanceUsed) {
        return new CareerDocument(student, null, TYPE_COVER_LETTER, versionNo, documentTitle, contentData, aiAssistanceUsed);
    }

    /** 이력서 신규 작성. 변경 시 기존 버전은 보존하고 새 스냅샷을 만든다. */
    public static CareerDocument createResume(AppUser student, Integer versionNo, String documentTitle,
                                              JsonNode contentData) {
        return new CareerDocument(student, null, TYPE_RESUME, versionNo, documentTitle, contentData, false);
    }

    /** 포트폴리오 항목 신규 생성. versionNo는 학생 기준 다음 항목 순번을 호출부에서 계산해 전달한다. */
    public static CareerDocument createPortfolio(AppUser student, Integer versionNo, String documentTitle,
                                                   JsonNode contentData, boolean aiAssistanceUsed) {
        return new CareerDocument(student, null, TYPE_PORTFOLIO, versionNo, documentTitle, contentData, aiAssistanceUsed);
    }

    /** 문서 제목·본문·AI 활용 여부를 갱신한다 (Dirty Checking). */
    public void updateContent(String documentTitle, JsonNode contentData, boolean aiAssistanceUsed) {
        this.documentTitle = documentTitle;
        this.contentData = contentData;
        this.aiAssistanceUsed = aiAssistanceUsed;
    }

    /** 공개 여부를 변경한다. */
    public void changeVisibility(boolean isPublic) {
        this.publicDocument = isPublic;
    }

    /** 첨부파일 묶음을 연결한다. */
    public void attachFileGroup(FileGroup fileGroup) {
        this.fileGroup = fileGroup;
    }

    /** 현재 문서 내용을 그대로 스냅샷한 새 버전 row를 만들어 반환한다 (기존 row는 변경하지 않음). */
    public CareerDocument createNextVersion(Integer nextVersionNo) {
        return new CareerDocument(this.student, this.fileGroup, this.documentType, nextVersionNo,
                this.documentTitle, this.contentData, this.aiAssistanceUsed);
    }
}
