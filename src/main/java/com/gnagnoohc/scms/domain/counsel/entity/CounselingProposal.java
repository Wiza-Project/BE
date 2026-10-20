package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Table(name = "counseling_proposal", uniqueConstraints = @UniqueConstraint(
        name = "uq_counseling_proposal_test_result",
        columnNames = {"psychological_test_result_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingProposal extends BaseCreatedAtEntity {

    // responseStatus 문자열 상수. 리터럴을 여러 메서드에 흩어 쓰면 오타를 컴파일러가 잡아주지 못하므로
    // 상태 비교·대입은 항상 이 상수를 통해서만 한다.
    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final String EXPIRED = "EXPIRED";
    // 제안 내용의 최대 길이(trim 후 기준)와 응답 기한 기간. 설계 문서 2장 확정값을 그대로 상수화한다.
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final long RESPONSE_PERIOD_DAYS = 7;

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
    private String responseStatus = PENDING;

    // 확정 정책(설계 9장)에 따라 모든 신규 제안의 응답 기한은 필수다. 물리 스키마도 같은 날짜의 DDL로
    // NOT NULL을 맞췄으므로(2026-09-05_counseling_proposal_response_deadline_not_null.sql) 여기서도
    // nullable을 허용하지 않는다.
    @Column(name = "response_deadline", nullable = false)
    private Instant responseDeadline;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /**
     * 상담사(ST200 only)가 학생별 최신 17점 이상 스트레스 결과에 제안을 생성하는 유일한 진입점이다.
     * studentId·제안자·응답기한·상태는 요청으로 받지 않고 이 팩토리가 직접 정하므로, 클라이언트가
     * 값을 지정하거나 변조할 수 있는 통로 자체를 만들지 않는다.
     *
     * <p>student와 result.getStudent()가 다르면 호출부가 학생 도출을 잘못한 것이므로, 서비스가
     * 이미 같은 학생임을 확인했더라도 엔티티가 마지막 방어선으로 한 번 더 검증한다(설계 3.2의
     * 불변식 2). 검증에 실패하면 학생·상담사·기한 등 어떤 필드도 초기화하지 않고 즉시 예외를 던진다.</p>
     */
    public static CounselingProposal create(
            AppUser student,
            Integer proposedBy,
            PsychologicalTestResult result,
            String proposalContent,
            Instant now
    ) {
        if (!student.getUserId().equals(result.getStudent().getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        CounselingProposal proposal = new CounselingProposal();
        proposal.student = student;
        proposal.proposedBy = proposedBy;
        proposal.psychologicalTestResult = result;
        proposal.proposalContent = normalizeContent(proposalContent);
        proposal.responseStatus = PENDING;
        proposal.responseDeadline = now.plus(RESPONSE_PERIOD_DAYS, ChronoUnit.DAYS);
        return proposal;
    }

    /**
     * DTO의 Bean Validation은 빠른 실패를 위한 보조 수단일 뿐이므로, DTO를 거치지 않고 create()를
     * 직접 호출하는 경로도 막을 수 있도록 최종 검증을 엔티티 생성 경계에 둔다. 앞뒤 공백을 제거한 뒤
     * 길이를 검사하므로, 원문이 길더라도 trim 결과가 정확히 1,000자면 통과한다.
     */
    private static String normalizeContent(String proposalContent) {
        String trimmed = proposalContent == null ? "" : proposalContent.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "제안 내용은 공백을 제외하고 1자 이상 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다."
            );
        }
        return trimmed;
    }

    /**
     * 학생이 이 제안을 수락해 만든 예약을 연결하고 상태를 ACCEPTED로 바꾼다.
     * 예약 생성은 서비스가 먼저 끝낸 뒤 그 결과만 넘겨받으므로, 이 메서드 자체는 예약을 새로
     * 만들지 않고 이미 만들어진 예약과 제안을 원자적으로 연결하는 마지막 단계만 담당한다.
     * 기한 전 PENDING이 아니면(이미 응답했거나 기한이 지났으면) 어떤 필드도 바꾸지 않고 예외로 막는다.
     */
    public void accept(CounselingReservation reservation, Instant now) {
        ensureRespondable(now);
        this.createdReservation = reservation;
        this.responseStatus = ACCEPTED;
        this.respondedAt = now;
    }

    /**
     * 학생이 사유 없이 즉시 거절한다. 예약은 만들지 않는다.
     * accept()와 같은 이유로 기한 전 PENDING이 아니면 상태를 바꾸지 않고 예외로 막는다.
     */
    public void reject(Instant now) {
        ensureRespondable(now);
        this.responseStatus = REJECTED;
        this.respondedAt = now;
    }

    /**
     * 저장된 responseStatus가 아직 PENDING이어도, 응답 기한이 이미 지났으면(now가 기한과 같거나
     * 더 늦으면) API 의미상 EXPIRED로 취급한다. 자동 만료는 학생의 응답이 아니므로 이 메서드는
     * respondedAt을 건드리지 않는다(그 값은 스케줄러의 저장 상태 정리에서도 null로 유지된다).
     * 이미 종결된 상태(ACCEPTED/REJECTED/EXPIRED)는 그대로 돌려준다.
     */
    public String effectiveResponseStatus(Instant now) {
        if (PENDING.equals(responseStatus) && !now.isBefore(responseDeadline)) {
            return EXPIRED;
        }
        return responseStatus;
    }

    /**
     * accept·reject가 공통으로 쓰는 응답 가능 여부 검사다. 저장 상태가 PENDING이 아니거나
     * (이미 응답했거나) 기한이 지났으면 COUNSELING_PROPOSAL_STATE_NOT_ALLOWED(S017)로 막는다.
     */
    private void ensureRespondable(Instant now) {
        if (!PENDING.equals(effectiveResponseStatus(now))) {
            throw new BusinessException(ErrorCode.COUNSELING_PROPOSAL_STATE_NOT_ALLOWED);
        }
    }
}
