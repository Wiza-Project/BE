package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
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

@Entity
@Getter
@Table(name = "student_job_relation", uniqueConstraints = @UniqueConstraint(
        name = "uq_student_job_relation_student_posting",
        columnNames = {"student_id", "job_posting_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentJobRelation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_job_relation_id", nullable = false)
    private Integer studentJobRelationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_consent_id", nullable = true)
    private UserConsent userConsent;

    @Column(name = "matching_score", nullable = true, precision = 6, scale = 2)
    private BigDecimal matchingScore;

    @Column(name = "matching_status", nullable = true, length = 20)
    private String matchingStatus;

    @Column(name = "matched_at", nullable = true)
    private Instant matchedAt;

    @Column(name = "bookmarked_at", nullable = true)
    private Instant bookmarkedAt;

    @Column(name = "applied_at", nullable = true)
    private Instant appliedAt;

    @Column(name = "canceled_at", nullable = true)
    private Instant canceledAt;

    @Column(name = "recommendation_source", nullable = true, length = 30)
    private String recommendationSource;

    @Column(name = "application_status", nullable = true, length = 20)
    private String applicationStatus;

    @Column(name = "selection_stage", nullable = true, length = 50)
    private String selectionStage;

    @Column(name = "selection_result", nullable = true, length = 30)
    private String selectionResult;

    /**
     * 온라인 채용 지원 처리 (비즈니스 상태 전이 메서드)
     *
     * <p><strong>[가이드라인 및 비즈니스 규칙]</strong></p>
     * <ul>
     *   <li>지원 상태를 'APPLIED'로 갱신하고 지원 일시(appliedAt)를 현재 시각으로 설정</li>
     *   <li>재지원 시나리오를 고려하여 기존 지원 취소 일시(canceledAt)를 null로 초기화</li>
     *   <li>개인정보 제3자 제공 동의 엔티티(UserConsent) 및 추천 경로(recommendationSource)를 연계하여 저장</li>
     *   <li>TODO: user 도메인의 ConsentService 완성 시 실제 UserConsent 인스턴스 주입 연동 필요 (현재 임시 null 허용, 서비스단에서 수정필요) </li>
     * </ul>
     *
     * @param userConsent          개인정보 제3자 제공 동의 엔티티 (nullable)
     * @param recommendationSource 추천 경로 (예: "STUDENT_DIRECT", "SYSTEM_RECOMMEND")
     */
    public void apply(UserConsent userConsent, String recommendationSource) {
        this.userConsent = userConsent;
        this.recommendationSource = recommendationSource;
        this.applicationStatus = "APPLIED";
        this.appliedAt = Instant.now();
        this.canceledAt = null;
    }

    /**
     * 온라인 채용 지원 취소 처리 (비즈니스 상태 전이 메서드)
     *
     * <p><strong>[가이드라인 및 비즈니스 규칙]</strong></p>
     * <ul>
     *   <li>지원 상태를 'CANCELED'로 갱신하고 지원 취소 일시(canceledAt)를 현재 시각으로 설정</li>
     *   <li>기존 지원 일시(appliedAt) 및 스크랩(bookmarkedAt) 이력은 변경하지 않고 보존</li>
     * </ul>
     */
    public void cancelApplication() {
        this.applicationStatus = "CANCELED";
        this.canceledAt = Instant.now();
    }

    /**
     * 관심 공고 스크랩 토글 (등록 / 해제)
     *
     * <p><strong>[가이드라인 및 비즈니스 규칙]</strong></p>
     * <ul>
     *   <li>bookmarkedAt이 null이면 신규 등록(현재 시각 저장, return true) 처리</li>
     *   <li>bookmarkedAt이 존재하면 스크랩 해제(null 설정, return false) 처리</li>
     *   <li>DB의 물리적 행(Row)을 삭제하지 않고 스크랩 일시 필드의 유무로 N:M 관계 상태를 관리</li>
     * </ul>
     *
     * @return 토글 이후 스크랩 상태 (true: 등록 완료, false: 해제 완료)
     */
    public boolean toggleBookmark() {
        if (this.bookmarkedAt == null) {
            this.bookmarkedAt = Instant.now();
            return true;
        } else {
            this.bookmarkedAt = null;
            return false;
        }
    }
}
