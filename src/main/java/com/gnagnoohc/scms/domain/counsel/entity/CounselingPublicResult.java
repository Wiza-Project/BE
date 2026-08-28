package com.gnagnoohc.scms.domain.counsel.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
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

import java.time.Instant;

/**
 * 학생에게 공개되는 상담 결과(요약·실행 계획)다. 비공개 원문(CounselingPrivateRecord)과는 물리적으로
 * 분리된 별개 행이며 서로 내용을 복사하지 않는다. 체크리스트 9번 범위에서는 회기당 versionNo=1 한 행만
 * 쓰고, 공개(publishedAt 값 있음) 이후에는 이 엔티티의 메서드가 수정·재공개를 막아 원본을 보존한다
 * — 정정은 체크리스트 10번에서 다음 버전을 새 행으로 추가하는 방식으로 처리한다. 공개 Setter는 두지 않는다.
 */
@Entity
@Getter
@Table(name = "counseling_public_result", uniqueConstraints = @UniqueConstraint(
        name = "uq_counseling_public_result_session_version",
        columnNames = {"counseling_session_id", "version_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingPublicResult extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "public_result_id", nullable = false)
    private Integer publicResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counseling_session_id", nullable = false)
    private CounselingSession counselingSession;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "result_summary", nullable = false, columnDefinition = "text")
    private String resultSummary;

    @Column(name = "action_plan", columnDefinition = "text")
    private String actionPlan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_up_data", columnDefinition = "jsonb")
    private JsonNode followUpData;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    /**
     * 회기당 최초 초안을 만든다. versionNo는 체크리스트 9번 범위에서 항상 1이고,
     * followUpData·correctionReason은 이번 범위에서 다루지 않으므로 null로 고정한다.
     */
    public static CounselingPublicResult createDraft(
            CounselingSession session, String resultSummary, String actionPlan, Integer counselorId
    ) {
        CounselingPublicResult result = new CounselingPublicResult();
        result.counselingSession = session;
        result.versionNo = 1;
        result.resultSummary = validateSummary(resultSummary);
        result.actionPlan = validateActionPlan(actionPlan);
        result.followUpData = null;
        result.correctionReason = null;
        result.publishedAt = null;
        result.createdBy = counselorId;
        return result;
    }

    /** 공개 전 초안 반복 저장. 이미 공개된 행은 원본 보존을 위해 수정을 막는다. */
    public void updateDraft(String resultSummary, String actionPlan) {
        if (isPublished()) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        this.resultSummary = validateSummary(resultSummary);
        this.actionPlan = validateActionPlan(actionPlan);
    }

    /** 일반 공개 처리. 이미 공개된 결과의 재공개는 막는다(내용·공개 시각을 덮어쓰지 않기 위함). */
    public void publish(Instant now) {
        if (isPublished()) {
            throw new BusinessException(ErrorCode.PUBLIC_RESULT_STATE_NOT_ALLOWED);
        }
        this.publishedAt = now;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /**
     * 공개 요약 검증 경계. 앞뒤 공백만 제거(strip)하고 내부 줄바꿈·공백은 보존한다.
     * 필수 입력이므로 공백 제외 1자 이상 3,000자 이하만 허용한다.
     */
    private static String validateSummary(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.isEmpty() || trimmed.length() > 3000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공개 요약은 공백 제외 1자 이상 3,000자 이하여야 합니다.");
        }
        return trimmed;
    }

    /**
     * 실행 계획 검증 경계. 선택 입력이라 null, 빈 문자열, 공백만 있는 값은 모두 null로 정규화한다.
     * 값이 있으면 앞뒤 공백 제거 후 3,000자 이하만 허용한다.
     */
    private static String validateActionPlan(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 3000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "실행 계획은 공백 제외 3,000자 이하여야 합니다.");
        }
        return trimmed;
    }
}
