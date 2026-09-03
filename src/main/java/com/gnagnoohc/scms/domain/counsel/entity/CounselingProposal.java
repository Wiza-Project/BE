package com.gnagnoohc.scms.domain.counsel.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "counseling_proposal", uniqueConstraints = @UniqueConstraint(
        name = "uq_counseling_proposal_test_result",
        columnNames = {"psychological_test_result_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingProposal extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counseling_proposal_id", nullable = false)
    private Integer counselingProposalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Column(name = "proposed_by", nullable = false)
    private Integer proposedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psychological_test_result_id", nullable = false)
    private PsychologicalTestResult psychologicalTestResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_reservation_id")
    private CounselingReservation createdReservation;

    @Column(name = "proposal_content", nullable = false, columnDefinition = "text")
    private String proposalContent;

    @Column(name = "response_status", nullable = false, length = 20)
    private String responseStatus = "PENDING";

    @Column(name = "response_deadline")
    private Instant responseDeadline;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
