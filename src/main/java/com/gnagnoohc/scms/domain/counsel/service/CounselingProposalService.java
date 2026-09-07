package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingProposalAcceptRequest;
import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingProposalCreateRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingProposalEligibleResultResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingProposalResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingProposal;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestResult;
import com.gnagnoohc.scms.domain.counsel.event.CounselingProposalCreatedEvent;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingProposalRepository;
import com.gnagnoohc.scms.domain.counsel.repository.PsychologicalTestResultRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.DbConstraintViolationMatcher;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 체크리스트 14 "스트레스 결과 기반 상담 제안"의 유스케이스와 트랜잭션 경계를 담당한다.
 * 일정 정원·시간 중복·동의 검증은 여기서 다시 구현하지 않고 CounselingReservationService의
 * 패키지 범위 진입점(createFromStressProposal)을 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingProposalService {

    // responseStatus 문자열 리터럴. CounselingProposal 내부 상수와 값이 같아야 하며, 이 값은
    // API 응답 필드(responseStatus)로도 그대로 노출되는 공개 상태값이라 여기서 비교해도 안전하다.
    private static final String PENDING = "PENDING";
    private static final String STRESS_TEST_TYPE = "STRESS";
    private static final BigDecimal MIN_ELIGIBLE_SCORE = BigDecimal.valueOf(17);

    private final CounselUserRepository counselUserRepository;
    private final CounselManagementAccessPolicy counselManagementAccessPolicy;
    private final PsychologicalTestResultRepository psychologicalTestResultRepository;
    private final CounselingProposalRepository counselingProposalRepository;
    private final CounselingReservationService counselingReservationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 학생별 최신 STRESS 결과 중 17점 이상·미제안 결과만 후보로 보여준다.
     * 이 조회는 잠금을 잡지 않으며 생성 권한을 예약하지도 않는다. 실제 생성 시점에 최신성·점수·
     * 중복을 모두 다시 검증한다(설계 5.1).
     */
    public PageResponse<CounselingProposalEligibleResultResponse> getEligibleResults(
            Integer counselorId,
            int page,
            int size
    ) {
        requireAllDirectScope(counselorId);
        PageRequest pageRequest = PageRequest.of(page, size);
        return PageResponse.from(psychologicalTestResultRepository
                .findEligibleResults(pageRequest)
                .map(CounselingProposalEligibleResultResponse::from));
    }

    /**
     * 후보 조회 뒤 새 검사 제출이 끼어드는 경쟁(설계 5.1)을 막기 위해 다음 순서로 처리한다.
     * 1) 결과 ID로 대상 학생만 우선 확인 2) 학생 행을 잠근다 3) 선택 결과와 학생의 최신 STRESS
     * 결과를 다시 잠금 조회한다 4) 두 결과가 같은 행이고 17점 이상이며 기존 제안이 없는지 확인한다
     * 5) 저장한다. 같은 결과 DB 유니크 충돌만 S015로 흡수하고 다른 무결성 오류는 그대로 다시 던진다.
     */
    @Transactional
    public CounselingProposalResponse create(CounselingProposalCreateRequest request, Integer counselorId) {
        requireAllDirectScope(counselorId);

        // 1) 아직 신뢰하지 않는 조회로 대상 학생 ID만 얻는다. 존재하지 않으면 결과 ID 탐색 방지를
        // 위해 "없음"과 "부적합"을 구분하지 않고 곧바로 S015로 응답한다.
        Integer studentId = psychologicalTestResultRepository.findById(request.psychologicalTestResultId())
                .map(result -> result.getStudent().getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_PROPOSAL_TARGET_NOT_AVAILABLE));

        // 2) 학생 행을 잠근다. 스트레스 결과 제출(StressTestService.submit)도 저장 전에 같은 학생
        // 행을 잠그므로, 이 잠금이 "새 검사 제출과 제안 생성"을 하나의 직렬화 지점으로 묶는다.
        AppUser student = counselUserRepository.findByIdForUpdate(studentId)
                .filter(this::isActiveStudent)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_PROPOSAL_TARGET_NOT_AVAILABLE));

        // 3) 학생 잠금 아래에서 선택 결과와 최신 결과를 다시 읽는다.
        PsychologicalTestResult selectedResult = psychologicalTestResultRepository
                .findByIdForUpdate(request.psychologicalTestResultId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_PROPOSAL_TARGET_NOT_AVAILABLE));
        PsychologicalTestResult latestResult = psychologicalTestResultRepository
                .findFirstByStudentUserIdAndTestTypeOrderByTestedAtDescPsychologicalTestResultIdDesc(
                        studentId, STRESS_TEST_TYPE
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_PROPOSAL_TARGET_NOT_AVAILABLE));

        // 4) 최신성·유형·점수·중복을 모두 확인한다. 어느 조건에서 걸렸는지 구분해 알려주지 않아
        // 결과 ID로 다른 학생의 검사 이력을 추측하지 못하게 한다.
        boolean isLatest = selectedResult.getPsychologicalTestResultId()
                .equals(latestResult.getPsychologicalTestResultId());
        boolean isStress = STRESS_TEST_TYPE.equals(selectedResult.getTestType());
        boolean scoreEligible = selectedResult.getTotalScore().compareTo(MIN_ELIGIBLE_SCORE) >= 0;
        boolean alreadyProposed = counselingProposalRepository
                .existsByPsychologicalTestResultPsychologicalTestResultId(
                        selectedResult.getPsychologicalTestResultId()
                );
        if (!isLatest || !isStress || !scoreEligible || alreadyProposed) {
            throw new BusinessException(ErrorCode.COUNSELING_PROPOSAL_TARGET_NOT_AVAILABLE);
        }

        Instant now = Instant.now();
        CounselingProposal proposal = CounselingProposal.create(
                student,
                counselorId,
                selectedResult,
                request.proposalContent(),
                now
        );
        CounselingProposal saved;
        try {
            // 애플리케이션 사전 검사(alreadyProposed)를 이미 통과했더라도, 그 확인과 저장 사이에
            // 동시 요청이 먼저 커밋되면 DB의 uq_counseling_proposal_test_result가 최종 방어선이
            // 된다. 이 삽입에서 발생 가능한 무결성 위반은 사실상 이 유니크 제약뿐이므로 S015로
            // 흡수하되, 다른 원인(예: FK 위반)까지 조용히 중복 제안으로 오인하지 않도록 이 삽입
            // 경로 하나에만 좁게 적용한다.
            saved = counselingProposalRepository.saveAndFlush(proposal);
        } catch (DataIntegrityViolationException e) {
            if (DbConstraintViolationMatcher.contains(e, "uq_counseling_proposal_test_result")) {
                throw new BusinessException(ErrorCode.COUNSELING_PROPOSAL_TARGET_NOT_AVAILABLE);
            }
            throw e;
        }
        eventPublisher.publishEvent(new CounselingProposalCreatedEvent(saved.getCounselingProposalId(), studentId));
        return CounselingProposalResponse.from(saved, now);
    }

    /** 로그인 학생 본인의 제안만 최신 생성순으로 반환한다. */
    public PageResponse<CounselingProposalResponse> getStudentProposals(Integer studentId, int page, int size) {
        if (!counselUserRepository.isActiveStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Instant now = Instant.now();
        PageRequest pageRequest = PageRequest.of(page, size);
        return PageResponse.from(counselingProposalRepository
                .findAllByStudentUserIdOrderByCreatedAtDescCounselingProposalIdDesc(studentId, pageRequest)
                .map(proposal -> CounselingProposalResponse.from(proposal, now)));
    }

    /**
     * 잠금 순서는 학생 행 → 학생 소유 제안 행 → (예약 서비스 내부의) 동의 행 → 일정 행이다(설계 5.3).
     * 예약 생성 전에 먼저 응답 가능 상태를 확인해, 이미 종결됐거나 기한이 지난 제안에 불필요한
     * 예약 행을 만들지 않는다. 실제 상태 전이는 예약 저장이 끝난 뒤 proposal.accept()가 같은
     * 조건을 한 번 더 검사하므로, 두 확인 사이에 다른 트랜잭션이 끼어들어도 accept()에서 막힌다.
     */
    @Transactional
    public CounselingProposalResponse accept(
            Integer proposalId,
            Integer studentId,
            CounselingProposalAcceptRequest request
    ) {
        AppUser student = getActiveStudentForUpdate(studentId);
        CounselingProposal proposal = getOwnedProposalForUpdate(proposalId, studentId);
        Instant now = Instant.now();
        if (!PENDING.equals(proposal.effectiveResponseStatus(now))) {
            throw new BusinessException(ErrorCode.COUNSELING_PROPOSAL_STATE_NOT_ALLOWED);
        }
        CounselingReservation reservation = counselingReservationService.createFromStressProposal(
                student,
                request.scheduleId(),
                request.consentId(),
                now
        );
        proposal.accept(reservation, now);
        return CounselingProposalResponse.from(proposal, now);
    }

    /** 요청 본문 없이 즉시 거절한다. 예약은 만들지 않는다. */
    @Transactional
    public CounselingProposalResponse reject(Integer proposalId, Integer studentId) {
        // 거절 자체는 학생 데이터를 바꾸지 않지만, 수락과 같은 잠금 순서(학생 → 제안)를 지켜
        // 같은 학생의 동시 수락·거절 요청이 항상 같은 지점에서 직렬화되게 한다.
        getActiveStudentForUpdate(studentId);
        CounselingProposal proposal = getOwnedProposalForUpdate(proposalId, studentId);
        Instant now = Instant.now();
        proposal.reject(now);
        return CounselingProposalResponse.from(proposal, now);
    }

    /** ST300 only·겸임·무역할은 모두 403으로 통일한다. */
    private void requireAllDirectScope(Integer counselorId) {
        if (counselManagementAccessPolicy.requireScope(counselorId)
                != CounselManagementAccessPolicy.Scope.ALL_DIRECT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private AppUser getActiveStudentForUpdate(Integer studentId) {
        return counselUserRepository.findByIdForUpdate(studentId)
                .filter(this::isActiveStudent)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private boolean isActiveStudent(AppUser user) {
        return "STUDENT".equals(user.getUserType()) && "ACTIVE".equals(user.getAccountStatus());
    }

    private CounselingProposal getOwnedProposalForUpdate(Integer proposalId, Integer studentId) {
        return counselingProposalRepository.findByIdAndStudentIdForUpdate(proposalId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNSELING_PROPOSAL_NOT_FOUND));
    }
}
