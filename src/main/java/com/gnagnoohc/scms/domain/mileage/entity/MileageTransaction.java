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
        return earnFromProgramCompletion(application, application.getProgram().getMileagePolicy(), postedAt);
    }

    /** 마일리지 영역에서 프로그램 유형으로 해석한 비교과 정책으로 적립 원장을 생성한다. */
    public static MileageTransaction earnFromProgramCompletion(
            ProgramApplication application,
            MileagePolicy policy,
            Instant postedAt
    ) {

        MileageTransaction transaction = new MileageTransaction();
        transaction.student = application.getStudent();
        transaction.mileagePolicy = policy;
        // 정책 기준은 프로그램 유형이지만, 원장에는 프로그램 자체의 핵심역량을 보존한다.
        transaction.competency = application.getProgram().getCompetency();
        transaction.sourceProgramApplication = application;
        transaction.transactionType = "EARN";
        transaction.points = policy.getPoints();
        transaction.transactionStatus = "POSTED";
        transaction.requestedBy = application.getStudent().getUserId();
        transaction.transactionReason = "비교과 프로그램 이수 자동 적립";
        transaction.postedAt = postedAt;
        return transaction;
    }

    /** 외부활동 신청을 승인할 때 정책에 등록된 점수로 적립 원장을 생성한다. */
    public static MileageTransaction earnFromExternalClaim(
            ExternalActivityClaim claim,
            Instant postedAt,
            Integer processedBy
    ) {
        MileagePolicy policy = claim.getMileagePolicy();

        MileageTransaction transaction = new MileageTransaction();
        transaction.student = claim.getStudent();
        transaction.mileagePolicy = policy;
        transaction.competency = claim.getActivityType().getCompetency();
        transaction.sourceExternalClaim = claim;
        transaction.transactionType = "EARN";
        transaction.points = policy.getPoints();
        transaction.transactionStatus = "POSTED";
        transaction.requestedBy = claim.getStudent().getUserId();
        transaction.processedBy = processedBy;
        transaction.transactionReason = "외부활동 마일리지 심사 승인 자동 적립";
        transaction.postedAt = postedAt;
        return transaction;
    }

    /** 승인된 외부활동 원장을 취소할 때 원거래를 보존하고 반대 부호의 역분개를 생성한다. */
    public static MileageTransaction reverseExternalClaim(
            MileageTransaction original,
            Integer processedBy,
            String reason,
            Instant postedAt
    ) {
        MileageTransaction transaction = new MileageTransaction();
        transaction.student = original.getStudent();
        transaction.mileagePolicy = original.getMileagePolicy();
        transaction.competency = original.getCompetency();
        transaction.reversalOfTransaction = original;
        transaction.transactionType = "REVERSE";
        transaction.points = original.getPoints().negate();
        transaction.transactionStatus = "POSTED";
        transaction.requestedBy = original.getRequestedBy();
        transaction.processedBy = processedBy;
        transaction.transactionReason = reason.trim();
        transaction.postedAt = postedAt;
        return transaction;
    }
}
