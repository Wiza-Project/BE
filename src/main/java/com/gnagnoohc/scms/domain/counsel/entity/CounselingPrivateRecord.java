package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 비공개 상담 원문은 서비스 인가와 민감정보 열람 감사로그가 필요하다. */
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
}
