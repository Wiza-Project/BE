package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 비공개 상담 원문은 서비스 인가와 민감정보 열람 감사로그가 필요하다. 초안은 회기당 한 행만 존재하며
 * 반복 저장은 같은 행을 덮어쓴다(versionNo는 항상 1) — 확정 이후에는 이 엔티티의 메서드가 모두 막아
 * 원본을 보존한다. 공개 Setter는 두지 않는다.
 */
@Entity @Getter
@Table(name = "counseling_private_record", uniqueConstraints = @UniqueConstraint(
        name = "uq_counseling_private_record_session_version", columnNames = {"counseling_session_id", "version_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingPrivateRecord extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "private_record_id", nullable = false) private Integer privateRecordId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "counseling_session_id", nullable = false) private CounselingSession counselingSession;
    @Column(name = "version_no", nullable = false) private Integer versionNo;
    @Column(name = "private_content", nullable = false, columnDefinition = "text") private String privateContent;
    @Column(name = "confirmed_by") private Integer confirmedBy;
    @Column(name = "confirmed_at") private Instant confirmedAt;

    /** 회기당 최초 초안을 만든다. versionNo는 항상 1이다. */
    public static CounselingPrivateRecord createDraft(CounselingSession session, String privateContent) {
        CounselingPrivateRecord record = new CounselingPrivateRecord();
        record.counselingSession = session;
        record.versionNo = 1;
        record.privateContent = validateContent(privateContent);
        record.confirmedBy = null;
        record.confirmedAt = null;
        return record;
    }

    /** 확정 전 초안 반복 저장. 확정된 기록은 원본 보존을 위해 수정을 막는다. */
    public void updateContent(String privateContent) {
        if (isConfirmed()) {
            throw new BusinessException(ErrorCode.PRIVATE_RECORD_STATE_NOT_ALLOWED);
        }
        this.privateContent = validateContent(privateContent);
    }

    /** 확정 처리. 이미 확정된 기록의 재확정은 막는다. */
    public void confirm(Integer confirmedBy, Instant confirmedAt) {
        if (isConfirmed()) {
            throw new BusinessException(ErrorCode.PRIVATE_RECORD_STATE_NOT_ALLOWED);
        }
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    /**
     * 원문 검증 경계. 앞뒤 공백만 제거(strip)하고 내부 줄바꿈·공백은 보존한다.
     * 공백 제외 1자 이상 10,000자 이하만 허용한다.
     */
    private static String validateContent(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.isEmpty() || trimmed.length() > 10000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비공개 기록은 공백 제외 1자 이상 10,000자 이하여야 합니다.");
        }
        return trimmed;
    }
}
