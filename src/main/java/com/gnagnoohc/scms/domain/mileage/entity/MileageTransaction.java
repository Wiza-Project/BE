package com.gnagnoohc.scms.domain.mileage.entity;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 학생별 마일리지 적립·차감 원장을 기록한다.
 * POSTED 거래의 합이 잔액이며 정정은 원행 변경 대신 반대 부호의 역분개로 기록한다.
 */
@Entity @Getter @Table(name = "mileage_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MileageTransaction extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mileage_transaction_id", nullable = false) private Integer mileageTransactionId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AppUser student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mileage_policy_id") private MileagePolicy mileagePolicy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competency_id", nullable = false) private Competency competency;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_program_application_id", unique = true) private ProgramApplication sourceProgramApplication;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_external_claim_id", unique = true) private ExternalActivityClaim sourceExternalClaim;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reversal_of_transaction_id", unique = true) private MileageTransaction reversalOfTransaction;
    @Column(name = "transaction_type", nullable = false, length = 20) private String transactionType;
    @Column(name = "points", nullable = false, precision = 10, scale = 2) private BigDecimal points;
    @Column(name = "transaction_status", nullable = false, length = 20) private String transactionStatus = "POSTED";
    @Column(name = "requested_by") private Integer requestedBy;
    @Column(name = "processed_by") private Integer processedBy;
    @Column(name = "transaction_reason", columnDefinition = "text") private String transactionReason;
    @Column(name = "posted_at") private Instant postedAt;

    /** 이수 완료된 비교과 프로그램의 사전 등록 점수로 적립 원장을 생성한다. */
    public static MileageTransaction earnFromProgramCompletion(
            ProgramApplication application,
            Instant postedAt
    ) {
        MileagePolicy policy = application.getProgram().getMileagePolicy();

        MileageTransaction transaction = new MileageTransaction();
        transaction.student = application.getStudent();
        transaction.mileagePolicy = policy;
        transaction.competency = policy.getActivityType().getCompetency();
        transaction.sourceProgramApplication = application;
        transaction.transactionType = "EARN";
        transaction.points = policy.getPoints();
        transaction.transactionStatus = "POSTED";
        transaction.requestedBy = application.getStudent().getUserId();
        transaction.transactionReason = "비교과 프로그램 이수 자동 적립";
        transaction.postedAt = postedAt;
        return transaction;
    }

    /** 증빙이 확인된 외부활동 마일리지 신청의 승인 적립 원장을 생성한다. */
    public static MileageTransaction earnFromExternalClaim(
            ExternalActivityClaim claim,
            BigDecimal points,
            Integer processedBy,
            Instant postedAt
    ) {
        MileagePolicy policy = claim.getMileagePolicy();

        MileageTransaction transaction = new MileageTransaction();
        transaction.student = claim.getStudent();
        transaction.mileagePolicy = policy;
        transaction.competency = claim.getActivityType().getCompetency();
        transaction.sourceExternalClaim = claim;
        transaction.transactionType = "EARN";
        transaction.points = points;
        transaction.transactionStatus = "POSTED";
        transaction.requestedBy = claim.getStudent().getUserId();
        transaction.processedBy = processedBy;
        transaction.transactionReason = "외부활동 마일리지 심사 승인";
        transaction.postedAt = postedAt;
        return transaction;
    }
}
