package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationAdminListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationBulkDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationCancelResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationSummaryResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationSurveyResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplyResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.event.ApplicationDecidedEvent;
import com.gnagnoohc.scms.domain.program.event.WaitlistSlotOpenedEvent;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramAttendanceRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramMileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.notification.ModuleCode;
import com.gnagnoohc.scms.global.common.notification.NotificationRequest;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.common.notification.NotificationType;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProgramApplicationService {

    private final ExtracurricularProgramRepository programRepository;
    private final ProgramApplicationRepository applicationRepository;
    private final ProgramAttendanceRepository attendanceRepository;
    private final ProgramMileageTransactionRepository mileageTransactionRepository;
    private final NotificationSender notificationSender;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final ConsentVerifier consentVerifier;

    /**
     * 학생의 프로그램 참여 신청을 접수한다. 매개변수 2개의 의미:
     *   programId : 신청할 프로그램의 PK (URL 경로에서 옴)
     *   studentId : 지금 로그인해서 이 요청을 보낸 학생의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
     */
    public ProgramApplyResponseDTO apply(Integer programId, Integer studentId) {

        Instant now = Instant.now();

        /**
         * (0) 필수 동의 확인 -------------------------------------------------------------
         * PROGRAM 모듈의 필수 동의(TERMS_OF_SERVICE, PERSONAL_INFO)를 모두 마쳤는지 게이트로
         * 체크한 뒤, 두 필수 동의 건 각각을 잠금 재검증한다. lockAndVerifyRequiredConsent() 안의
         * findCurrentValidConsent()는 잠금이 없으므로 후보 ID만 얻는 용도로만 쓰고,
         * requireOwnedValidConsent()로 그 ID를 다시 넘겨 UserConsent 행에 비관적 락(findByIdForUpdate)을
         * 걸고 재검증한다. UserConsentService.withdraw()도 같은 락을 쓰므로, 신청과 철회가 같은 동의
         * 행을 두고 직렬화된다 — 락을 먼저 잡은 쪽이 이기고, 철회가 먼저 커밋되면 재검증에서
         * withdrawnAt이 채워진 것을 보고 FORBIDDEN이 되어 아래에서 REQUIRED_CONSENT_NOT_AGREED로
         * 변환된다(CounselingReservationService.create()와 동일 패턴). 두 동의 행을 잠글 때는
         * 항상 TERMS_OF_SERVICE -> PERSONAL_INFO 순서로 고정해, 이 메서드 안에서 여러 동의 행을
         * 동시에 잠그는 다른 경로가 생기더라도 락 순서가 엇갈려 데드락이 나지 않게 한다.
         * 이 시점의 now는 동의 확인 asOf 시각일 뿐, 아래 모집기간 검사·저장 시각에는 쓰지 않는다 —
         * 락 대기·정원 계산 등으로 그 사이 시간이 흐를 수 있어 (b), (e)에서 각각 다시 계산한다.
         * 이 최초 검사는 락을 잡기 전에 미리 걸러내는 실패-우선(fail-fast) 용도이고, 그 사이 동의가
         * 철회·만료됐을 가능성에 대비한 최종 재검증은 (e) 저장 직전에 다시 수행한다.
         */
        if (!consentVerifier.hasAgreedAllRequired(studentId, ConsentModuleCode.PROGRAM, now)) {
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }
        lockAndVerifyRequiredConsent(studentId, ConsentType.TERMS_OF_SERVICE, now);
        UserConsent userConsent = lockAndVerifyRequiredConsent(studentId, ConsentType.PERSONAL_INFO, now);

        /**
         * (a) 존재 확인 + 락 --------------------------------------------------------
         * findByIdForUpdate는 이 프로그램 row에 비관적 락을 걸어서, 아래 (c) 정원 계산과
         * (d) INSERT 사이에 다른 신청 요청이 끼어들어 순번이 꼬이는 것을 막는다.
         */
        ExtracurricularProgram program = programRepository.findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        /**
         * (b) 신청 가능 기간인지 확인 --------------------------------------------------
         * ProgramService.update/delete와 같은 이유로, programStatus 컬럼(스케줄러가 최대 1분 지연으로 갱신)을
         * 믿지 않고 지금 시각을 모집 시작/종료 시각과 직접 비교한다. 락 획득 직후 now를 다시 계산해서
         * (0) 동의 확인에 걸린 지연이 이 검사에 섞이지 않게 한다 — program row는 이미 락으로 잡혀 있어
         * recruitmentStartsAt/EndsAt 값 자체는 이 트랜잭션 동안 바뀌지 않는다.
         */
        now = Instant.now();
        if (now.isBefore(program.getRecruitmentStartsAt()) || !now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.APPLICATION_PERIOD_CLOSED);
        }

        /**
         * (c) 기존 신청 이력 확인 --------------------------------------------------------
         * program_id+student_id 조합은 uq_program_application_program_student 유니크 제약이 걸려있어
         * 이 학생의 이 프로그램에 대한 신청 건은 항상 0건 또는 1건이다. 기존 건이 있는데 그 상태가
         * CANCELLED(학생이 스스로 취소)가 아니면, 즉 APPLIED/WAITLISTED/APPROVED(진행 중)거나
         * REJECTED(운영부서가 반려, 재신청 불가 정책)이면 재신청을 허용하지 않는다.
         * CANCELLED인 경우에만 아래 (e)에서 기존 row를 새 신청으로 되살린다(INSERT 대신 UPDATE).
         */
        Optional<ProgramApplication> existing =
                applicationRepository.findByProgram_ProgramIdAndStudent_UserIdForUpdate(programId, studentId);
        if (existing.isPresent() && !ApplicationStatus.CANCELLED.name().equals(existing.get().getApplicationStatus())) {
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }

        /**
         * (d) 정원 확인 및 상태 결정 ----------------------------------------------------
         * 정원을 차지하는 상태는 APPLIED와 APPROVED 둘 다이므로(APPLIED가 승인되어도 슬롯은 그대로
         * 점유된 채 유지됨), 둘을 합산한 건수가 아직 정원(capacity)보다 적으면 정원 내 신청으로,
         * 그렇지 않으면 대기 신청("WAITLISTED")으로 접수하고 다음 대기순번을 매긴다.
         * APPLIED만 세면, APPLIED 전원이 APPROVED로 전환된 직후 그 자리가 빈 것처럼 보여 새 신청자를
         * 다시 APPLIED로 받아들여 정원을 초과시키는 버그가 있었다.
         */
        long occupiedCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                programId, List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));

        ApplicationStatus status;
        Integer waitlistOrder;
        if (occupiedCount < program.getCapacity()) {
            status = ApplicationStatus.APPLIED;
            waitlistOrder = null;
        } else {
            status = ApplicationStatus.WAITLISTED;
            waitlistOrder = applicationRepository.findMaxWaitlistOrderByProgramId(programId) + 1;
        }

        // (e) 실제 DB 반영 -------------------------------------------------------------
        // (c) 기존 신청 조회, (d) 정원 계산 등으로 (b) 이후에도 시간이 흐를 수 있으므로, 저장 직전 시각으로
        // 한 번 더 마감 여부를 재확인한다. 시작 시각은 시간이 거꾸로 흐르지 않으므로 재검사가 필요 없다.
        // 이 시각이 그대로 아래 저장·응답 시각(now)으로 쓰인다.
        now = Instant.now();
        if (!now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.APPLICATION_PERIOD_CLOSED);
        }

        /**
         * (0) 이후 프로그램 행 락 대기·기존 신청 조회·정원 계산 등으로 시간이 흘렀을 수 있어, 그 사이
         * 동의가 철회되거나 만료되지 않았는지 저장 직전 시각(now)으로 다시 검증한다. 증빙으로 저장할
         * UserConsent도 다시 조회해 그 결과로 덮어쓴다 — (0)에서 구한 값을 그대로 쓰면, 그 사이 새 버전
         * 동의로 갱신된 경우 이미 낡은 증빙 ID를 저장하게 된다.
         */
        if (!consentVerifier.hasAgreedAllRequired(studentId, ConsentModuleCode.PROGRAM, now)) {
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }
        userConsent = consentVerifier
                .findCurrentValidConsent(studentId, ConsentModuleCode.PROGRAM, ConsentType.PERSONAL_INFO, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED));

        Integer applicationId;
        if (existing.isPresent()) {
            // 취소됐던 기존 row를 새 신청으로 되살린다. WHERE 절이 여전히 CANCELLED인지 재확인하므로,
            // 0건이면 그 사이 다른 요청이 먼저 처리한 것이니 "이미 신청됨"으로 처리한다.
            int updatedRows = applicationRepository.reviveApplication(
                    existing.get().getApplicationId(), status.name(), waitlistOrder,
                    userConsent.getUserConsentId(), now);
            if (updatedRows == 0) {
                throw new BusinessException(ErrorCode.ALREADY_APPLIED);
            }
            applicationId = existing.get().getApplicationId();
            // 이전 사이클(취소 전)에 기록된 출결이 남아있다면, 새 사이클의 이수 판정에 섞이지 않도록 지운다.
            attendanceRepository.deleteByApplication_ApplicationId(applicationId);
        } else {
            try {
                applicationId = applicationRepository.insertApplication(
                        programId, studentId, status.name(), waitlistOrder, false,
                        userConsent.getUserConsentId(), now);
            } catch (DataIntegrityViolationException e) {
                // uq_program_application_program_student 유니크 제약 위반 = 이미 신청한 프로그램(동시 요청 경쟁).
                throw new BusinessException(ErrorCode.ALREADY_APPLIED);
            }
        }

        return new ProgramApplyResponseDTO(
                applicationId, programId, status.name(), status.getLabel(), waitlistOrder, now);
    }

    /**
     * PROGRAM 모듈의 특정 필수 동의 건을 찾아 비관적 락으로 재검증한다. apply()의 (0) 단계에서
     * 필수 동의 종류마다 이 메서드를 호출한다 — 여러 종류를 잠글 때는 항상 같은 순서로 호출해야
     * 데드락을 피할 수 있다.
     */
    private UserConsent lockAndVerifyRequiredConsent(Integer studentId, ConsentType consentType, Instant now) {
        Integer candidateConsentId = consentVerifier
                .findCurrentValidConsent(studentId, ConsentModuleCode.PROGRAM, consentType, now)
                .map(UserConsent::getUserConsentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED));
        try {
            return consentVerifier.requireOwnedValidConsent(
                    candidateConsentId, studentId, ConsentModuleCode.PROGRAM, consentType, now);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.FORBIDDEN) {
                throw e;
            }
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }
    }

    /**
     * 운영부서가 신청 건을 승인한다. 정원(capacity) 내에서만 승인할 수 있다 —
     * 신청 시점에 대기(WAITLISTED)로 분류됐던 건이라도, 다른 신청이 반려되어 자리가 나면 승인할 수 있다.
     */
    public ProgramApplicationDecisionResponseDTO approve(Integer programId, Integer applicationId, Integer staffId) {
        /**
         * 프로그램 row에 락을 걸어, "현재 승인 건수 확인"과 "실제 승인 반영" 사이에
         * 다른 승인 요청이 끼어들어 정원을 초과하는 경쟁 조건을 막는다 (apply()와 동일한 이유).
         * apply()가 프로그램 행 → 신청 행 순서로 잠그는 것과 반드시 같은 순서로 잠가야 한다 —
         * 순서가 반대이면 동시에 실행되는 apply()와 approve()가 서로의 락을 기다리며 DB 데드락이 날 수 있다.
         */
        ExtracurricularProgram program = programRepository.findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        ProgramApplication application = findApplicationForUpdate(programId, applicationId);

        /**
         * APPLIED → APPROVED는 이미 점유하고 있던 슬롯을 유지하는 전이일 뿐이라 정원을 새로 차지하지
         * 않으므로 재검사하지 않는다(재검사하면 이미 정원 내로 받아둔 신청조차 승인하지 못하는 버그가
         * 된다). WAITLISTED → APPROVED만 새 슬롯을 점유하므로 그 경우에만, APPLIED와 APPROVED를
         * 합산한 점유 건수로 정원을 검사한다(apply()와 동일한 집계 기준).
         */
        if (ApplicationStatus.WAITLISTED.name().equals(application.getApplicationStatus())) {
            long occupiedCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                    programId, List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));
            if (occupiedCount >= program.getCapacity()) {
                throw new BusinessException(ErrorCode.PROGRAM_CAPACITY_EXCEEDED);
            }
        }

        return applyDecision(application, ApplicationStatus.APPROVED, null, staffId);
    }

    /**
     * 학생이 자신의 대기(WAITLISTED) 신청을 스스로 확정한다. 대기 중 자리가 났다는 알림을 받은 뒤,
     * 운영부서 승인을 기다리지 않고 본인이 직접 확정할 수 있게 한다. apply()를 그대로 재호출하면
     * (c) 기존 신청 이력 확인에서 ALREADY_APPLIED로 막히므로(WAITLISTED는 CANCELLED가 아님), approve()와
     * 별도의 경로로 둔다. approve()와 정원 검사 로직은 같지만, 처리 주체가 운영부서(staffId)가 아니라
     * 신청 본인(studentId)이라는 점과, 대상 상태를 WAITLISTED로만 한정한다는 점이 다르다 — findApplicationForUpdate()는
     * APPLIED도 허용해 승인/반려 양쪽에 쓰이지만, 확정은 이미 정원 내로 확보된 APPLIED 건에는 의미가 없다.
     */
    public ProgramApplicationDecisionResponseDTO confirm(Integer programId, Integer applicationId, Integer studentId) {
        /**
         * apply()/approve()와 같은 이유로 프로그램 행을 신청 행보다 먼저 잠가, 아래 정원 확인과 실제
         * 확정 반영 사이에 다른 요청(다른 대기자의 확정, 운영부서의 승인 등)이 끼어들어 정원을 초과하는
         * 경쟁 조건을 막는다. apply()/approve()/reject()/cancel()과 반드시 같은 순서로 잠가야 데드락을 피한다.
         */
        ExtracurricularProgram program = programRepository.findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        ProgramApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        // cancel()과 같은 이유로, programId·학생 본인 소유가 아니면 존재 여부를 구분하지 않고 APPLICATION_NOT_FOUND로 처리한다.
        if (!application.getProgram().getProgramId().equals(programId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        if (!application.getStudent().getUserId().equals(studentId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 확정은 대기(WAITLISTED) 상태에서만 의미가 있다. 이미 APPLIED/APPROVED면 확정할 게 없고,
        // REJECTED/CANCELLED면 이미 종결된 상태다.
        if (!ApplicationStatus.WAITLISTED.name().equals(application.getApplicationStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        /**
         * 필수 동의 재확인 -----------------------------------------------------------
         * 확정은 정원 슬롯을 새로 점유해 참여를 확정짓는 행위라 apply()의 (0)단계와 성격이 같다.
         * 대기를 건 시점과 자리가 나 확정하는 시점 사이에 동의를 철회했을 수 있으므로 다시 게이트로
         * 체크한다. 다만 confirm()은 user_consent_id를 새로 쓰지 않으므로(updateDecision이 그
         * 컬럼을 건드리지 않음), apply()처럼 비관적 락으로 재검증하며 증빙을 갱신할 필요는 없다.
         */
        Instant now = Instant.now();
        if (!consentVerifier.hasAgreedAllRequired(studentId, ConsentModuleCode.PROGRAM, now)) {
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }

        // apply()/cancel()과 같은 이유로, programStatus 컬럼을 믿지 않고 지금 시각을 모집 종료 시각과 직접 비교한다.
        if (!now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.APPLICATION_PERIOD_CLOSED);
        }

        // WAITLISTED -> APPROVED는 새 슬롯을 점유하는 전이이므로, approve()와 동일한 기준으로 정원을 검사한다.
        long occupiedCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                programId, List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));
        if (occupiedCount >= program.getCapacity()) {
            throw new BusinessException(ErrorCode.PROGRAM_CAPACITY_EXCEEDED);
        }

        return applyDecision(application, ApplicationStatus.APPROVED, null, studentId);
    }

    /**
     * 운영부서가 신청 건을 반려한다. 반려 사유(reason)는 컨트롤러 단 @NotBlank 검증으로 항상 채워져 있다.
     * approve()와 동일한 이유로 프로그램 행을 신청 행보다 먼저 잠근다 — apply()가 정원을 확인하는 사이
     * reject()가 프로그램 행 락 없이 자리를 비우면, apply()가 그 빈 자리를 보지 못하고 대기자로
     * 잘못 접수하는 경쟁 조건이 있었다.
     */
    public ProgramApplicationDecisionResponseDTO reject(Integer programId, Integer applicationId, String reason, Integer staffId) {
        programRepository.findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        ProgramApplication application = findApplicationForUpdate(programId, applicationId);
        String previousStatus = application.getApplicationStatus();

        ProgramApplicationDecisionResponseDTO response =
                applyDecision(application, ApplicationStatus.REJECTED, reason, staffId);

        /**
         * APPLIED였던 건의 반려는 정원 슬롯을 하나 비운다(cancel()의 (g)단계와 같은 기준).
         * WAITLISTED였던 건은 애초에 정원 밖이었으므로 반려돼도 새로 열리는 자리가 없어 발행하지 않는다.
         */
        if (ApplicationStatus.APPLIED.name().equals(previousStatus)) {
            eventPublisher.publishEvent(
                    new WaitlistSlotOpenedEvent(programId, application.getProgram().getProgramName()));
        }

        return response;
    }

    /**
     * 운영부서가 여러 신청 건을 한 번에 승인한다. FE 신청관리 화면에서 체크박스로 여러 학생을 선택해
     * "선택 승인"을 누르는 흐름에 대응한다. 기존 단건 approve()를 건별로 그대로 재사용하되, 한 건이
     * 실패(예: 처리 도중 정원이 차버림)하더라도 나머지 건 처리를 막지 않기 위해 BusinessException을
     * 그 건에서만 잡아 실패 목록에 담고 계속 진행한다. 이 메서드 전체가 하나의 트랜잭션이라, 실패한 건은
     * 애초에 UPDATE가 실행되지 않았을 뿐이므로 별도의 트랜잭션 분리(REQUIRES_NEW) 없이도 부분 성공이 자연스럽게 성립한다.
     */
    public ProgramApplicationBulkDecisionResponseDTO bulkApprove(Integer programId, List<Integer> applicationIds, Integer staffId) {
        return bulkDecide(applicationIds, id -> approve(programId, id, staffId));
    }

    // 운영부서가 여러 신청 건을 한 번에 반려한다. bulkApprove와 동일한 방식이며, 반려 사유는 선택된 모든 건에 공통 적용된다.
    public ProgramApplicationBulkDecisionResponseDTO bulkReject(Integer programId, List<Integer> applicationIds, String reason, Integer staffId) {
        return bulkDecide(applicationIds, id -> reject(programId, id, reason, staffId));
    }

    private ProgramApplicationBulkDecisionResponseDTO bulkDecide(
            List<Integer> applicationIds,
            java.util.function.Function<Integer, ProgramApplicationDecisionResponseDTO> decide) {
        List<ProgramApplicationDecisionResponseDTO> succeeded = new ArrayList<>();
        List<ProgramApplicationBulkDecisionResponseDTO.Failure> failed = new ArrayList<>();
        for (Integer applicationId : applicationIds) {
            try {
                succeeded.add(decide.apply(applicationId));
            } catch (BusinessException e) {
                failed.add(new ProgramApplicationBulkDecisionResponseDTO.Failure(
                        applicationId, e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
            }
        }
        return new ProgramApplicationBulkDecisionResponseDTO(succeeded, failed);
    }

    /**
     * 학생이 스스로 자신의 참여 신청을 취소한다. 모집 기간이 끝나지 않은 경우에만 취소할 수 있다.
     *   studentId : 지금 로그인해서 이 요청을 보낸 학생의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
     */
    public ProgramApplicationCancelResponseDTO cancel(Integer programId, Integer applicationId, Integer studentId, String reason) {

        /**
         * (a) 프로그램 행 락 -----------------------------------------------------------
         * apply()/approve()/reject()와 같은 이유로, 신청 행보다 프로그램 행을 먼저 잠가서 apply()의
         * 정원 확인과 이 취소 사이의 경쟁 조건을 막는다. 이 때문에 programId와 applicationId가
         * 모두 잘못된 요청은 (이전처럼 APPLICATION_NOT_FOUND가 아니라) PROGRAM_NOT_FOUND가 된다 —
         * 프로그램 행 락이 신청 행 조회보다 먼저 실행되므로 의도된 API 계약 변경이다.
         */
        programRepository.findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        // (b) 존재 확인 + 락 --------------------------------------------------------
        ProgramApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        /**
         * (c) 요청 경로의 programId, 로그인한 학생 본인 소유가 맞는지 확인 -----------------------
         * 존재 여부를 굳이 구분해서 노출하지 않기 위해, 승인/반려의 programId 불일치 처리와 같은 방식으로
         * 두 경우 모두 APPLICATION_NOT_FOUND를 던진다.
         */
        if (!application.getProgram().getProgramId().equals(programId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        if (!application.getStudent().getUserId().equals(studentId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        /**
         * (d) 이미 반려/취소된 건이 아닌지 확인 --------------------------------------------
         * 취소는 APPLIED/WAITLISTED/APPROVED 상태에서만 가능하다 (REJECTED/CANCELLED는 이미 종결된 상태).
         */
        String currentStatus = application.getApplicationStatus();
        if (!ApplicationStatus.APPLIED.name().equals(currentStatus)
                && !ApplicationStatus.WAITLISTED.name().equals(currentStatus)
                && !ApplicationStatus.APPROVED.name().equals(currentStatus)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_CANCELED);
        }

        /**
         * (e) 모집 기간이 끝나지 않았는지 확인 ------------------------------------------------
         * apply()와 같은 이유로, programStatus 컬럼을 믿지 않고 지금 시각을 모집 종료 시각과 직접 비교한다.
         */
        Instant now = Instant.now();
        if (!now.isBefore(application.getProgram().getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        /**
         * (f) 실제 DB 반영 -------------------------------------------------------------
         * updateCancellation의 WHERE 절에도 (d)/(e)와 같은 조건이 걸려있어, 이 확인과 실제 UPDATE 사이의
         * 경쟁 조건으로 0개의 row만 바뀌었다면 역시 "지금은 취소할 수 없는 상태였다"는 뜻이다
         * (ProgramService.delete의 deletedRows == 0 처리와 동일한 이유).
         */
        int updatedRows = applicationRepository.updateCancellation(applicationId, reason, now);
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        /**
         * (g) 정원 슬롯이 실제로 비었다면 대기 1순위에게 알림 ------------------------------------
         * WAITLISTED는 애초에 정원 외였으므로, 그 상태였던 신청이 취소된 경우는 자리 발생이 아니다.
         * APPLIED/APPROVED만 정원을 차지하던 상태이므로 그 경우에만 대기자에게 알린다.
         */
        if (ApplicationStatus.APPLIED.name().equals(currentStatus)
                || ApplicationStatus.APPROVED.name().equals(currentStatus)) {
            eventPublisher.publishEvent(
                    new WaitlistSlotOpenedEvent(programId, application.getProgram().getProgramName()));
        }

        /**
         * (h) 취소 직후 기준 잔여 정원 계산 --------------------------------------------------
         * (f)의 UPDATE가 이미 반영된 뒤라 이 신청 건은 더 이상 APPLIED/APPROVED로 집계되지 않으므로,
         * 이전 상태가 무엇이었든 다시 세기만 하면 취소 반영 직후의 정확한 점유 건수를 얻는다.
         */
        long occupiedCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                programId, List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));
        int remainingCapacity = Math.max((int) (application.getProgram().getCapacity() - occupiedCount), 0);

        return new ProgramApplicationCancelResponseDTO(
                applicationId, programId, ApplicationStatus.CANCELLED.name(), ApplicationStatus.CANCELLED.getLabel(),
                reason, now, remainingCapacity, application.getProgram().getRecruitmentEndsAt());
    }

    /**
     * 학생이 자신의 전체 신청 현황을 최신순으로 조회한다. 조회 전용이라 클래스 레벨
     * @Transactional을 readOnly = true로 오버라이드한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProgramApplicationSummaryResponseDTO> listMyApplications(Integer studentId, Pageable pageable) {
        Page<ProgramApplication> applications = applicationRepository.findAllByStudentId(studentId, pageable);
        List<Integer> applicationIds = applications.getContent().stream()
                .map(ProgramApplication::getApplicationId).toList();
        Map<Integer, ProgramAttendanceRepository.AttendanceCountProjection> attendanceByApplication =
                summarizeAttendance(applicationIds);
        Map<Integer, BigDecimal> earnedPointsByApplication = summarizeEarnedMileage(applicationIds);
        Map<Integer, Long> occupiedCountByProgram = summarizeOccupiedCount(applications.getContent());
        return PageResponse.from(applications.map(a -> toSummary(
                a, attendanceByApplication.get(a.getApplicationId()), earnedPointsByApplication.get(a.getApplicationId()),
                occupiedCountByProgram.getOrDefault(a.getProgram().getProgramId(), 0L))));
    }

    /**
     * 신청 내역 목록 한 페이지에 등장하는 프로그램들의 현재 점유 건수(APPLIED/APPROVED)를 한 번의
     * GROUP BY 쿼리로 배치 조회한다(summarizeAttendance/summarizeEarnedMileage와 같은 이유의 N+1 방지).
     * 같은 프로그램에 여러 신청 건이 나타날 수는 없지만(uq_program_application_program_student), 페이지 안에
     * 서로 다른 프로그램 여러 개가 섞여 있을 수 있어 programId 목록으로 한 번에 조회한다.
     */
    private Map<Integer, Long> summarizeOccupiedCount(List<ProgramApplication> applications) {
        List<Integer> programIds = applications.stream()
                .map(a -> a.getProgram().getProgramId()).distinct().toList();
        if (programIds.isEmpty()) {
            return Map.of();
        }
        return applicationRepository.countActiveApplicantsByProgramIds(programIds).stream()
                .collect(Collectors.toMap(
                        ProgramApplicationRepository.ProgramApplicantCount::getProgramId,
                        ProgramApplicationRepository.ProgramApplicantCount::getCount));
    }

    /**
     * 신청 내역 목록 한 페이지에 해당하는 신청 건들의 출석 집계를 한 번의 쿼리로 배치 조회한다(N+1 방지).
     *
     * @param applicationIds 집계 대상 신청 건 PK 목록
     * @return 신청 건 PK를 key로 하는 출석 집계 결과 맵
     */
    private Map<Integer, ProgramAttendanceRepository.AttendanceCountProjection> summarizeAttendance(List<Integer> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        return attendanceRepository.countAttendanceByApplicationIds(applicationIds).stream()
                .collect(Collectors.toMap(
                        ProgramAttendanceRepository.AttendanceCountProjection::getApplicationId, p -> p));
    }

    /**
     * 신청 내역 목록 한 페이지에 해당하는 신청 건들의 확정 적립 마일리지를 한 번의 쿼리로 배치 조회한다(N+1 방지).
     *
     * @param applicationIds 집계 대상 신청 건 PK 목록
     * @return 신청 건 PK를 key로 하는 확정 적립 마일리지 맵
     */
    private Map<Integer, BigDecimal> summarizeEarnedMileage(List<Integer> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        return mileageTransactionRepository.findPostedPointsByApplicationIds(applicationIds).stream()
                .collect(Collectors.toMap(
                        ProgramMileageTransactionRepository.EarnedPointsProjection::getApplicationId,
                        ProgramMileageTransactionRepository.EarnedPointsProjection::getPoints));
    }

    /**
     * 운영부서가 신청관리/이수판정 화면에서, 프로그램 하나의 전체 신청자를 조회한다(누가 신청했는지 이름/학번까지 필요하다는
     * 점이 listMyApplications와 다르다). 지금까지는 이 목록 자체를 내려주는 API가 없어서 스태프 화면에서 신청자 정보를
     * 아예 보여줄 수 없었다 — 승인/반려/일괄승인/일괄반려는 applicationId를 미리 알아야 호출할 수 있는데, 그 id 자체를
     * 조회할 방법이 없었기 때문이다. keyword는 학생 이름/학번을 대상으로 부분 일치 검색한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProgramApplicationAdminListItemResponseDTO> listByProgram(
            Integer programId, String status, String keyword, Pageable pageable) {
        if (!programRepository.existsById(programId)) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_FOUND);
        }
        String normalizedKeyword = StringUtils.hasText(keyword) ? escapeLikeKeyword(keyword.trim()) : null;
        Page<ProgramApplication> applications = applicationRepository.findAllByProgramIdAndStatus(
                programId, status, normalizedKeyword, pageable);
        return PageResponse.from(applications.map(ProgramApplicationAdminListItemResponseDTO::from));
    }

    /**
     * LIKE 패턴에서 특별한 의미를 갖는 문자(%, _)를 리터럴 문자로 취급되도록 이스케이프한다.
     * 이스케이프 문자 자체(!)를 가장 먼저 이스케이프해야, 뒤이어 %/_ 앞에 붙이는 !가 원본 키워드의 !와 섞이지 않는다.
     * findAllByProgramIdAndStatus의 JPQL LIKE 절에 걸린 ESCAPE '!'와 반드시 짝을 맞춰야 한다.
     */
    private static String escapeLikeKeyword(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * 학생이 자신의 신청 건에 대한 만족도 설문을 "제출 완료"로 표시한다. 개별 문항/응답 내용을 저장하는
     * 기능은 이번 범위에서 제외되었으므로(저장할 엔티티가 없음), 이 API는 ProgramApplication.surveyCompleted
     * 플래그만 갱신한다 — 실제 설문 UI는 프론트가 자체적으로 진행하고, 마지막에 이 API만 호출해 "완료" 표시를 남긴다.
     */
    public ProgramApplicationSurveyResponseDTO completeSurvey(Integer programId, Integer applicationId, Integer studentId) {
        ProgramApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        // cancel()과 같은 이유로, programId·학생 본인 소유가 아니면 존재 여부를 구분하지 않고 APPLICATION_NOT_FOUND로 처리한다.
        if (!application.getProgram().getProgramId().equals(programId)
                || !application.getStudent().getUserId().equals(studentId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        int updatedRows = applicationRepository.markSurveyCompleted(applicationId, studentId, Instant.now());
        if (updatedRows == 0) {
            /**
             * 위에서 이미 존재/소유를 확인했으므로, 여기서 0건이라면 승인(APPROVED) 상태가 아니라는 뜻이다
             * (markSurveyCompleted의 WHERE 절에도 같은 조건이 걸려있다 — updateDecision과 동일한 경쟁 조건 방지 패턴).
             */
            throw new BusinessException(ErrorCode.SURVEY_NOT_AVAILABLE);
        }

        return new ProgramApplicationSurveyResponseDTO(applicationId, true);
    }

    /**
     * 신청 엔티티와, 별도로 배치 조회해온 출석 집계·확정 적립 마일리지를 조합해 목록 응답 DTO 한 건을 만든다.
     * attendance가 null(해당 신청 건에 출결 기록이 아예 없음)이면 attendanceRate는 null로 내려간다.
     */
    private ProgramApplicationSummaryResponseDTO toSummary(
            ProgramApplication a,
            ProgramAttendanceRepository.AttendanceCountProjection attendance,
            BigDecimal earnedMileagePoints,
            long occupiedCount) {
        ApplicationStatus status = ApplicationStatus.valueOf(a.getApplicationStatus());
        int totalAttendanceCount = attendance != null ? attendance.getTotalCount().intValue() : 0;
        int presentAttendanceCount = attendance != null ? attendance.getPresentCount().intValue() : 0;
        Double attendanceRate = totalAttendanceCount > 0
                ? presentAttendanceCount * 100.0 / totalAttendanceCount
                : null;
        int remainingCapacity = Math.max(a.getProgram().getCapacity() - (int) occupiedCount, 0);
        return new ProgramApplicationSummaryResponseDTO(
                a.getApplicationId(), a.getProgram().getProgramId(), a.getProgram().getProgramName(),
                a.getApplicationStatus(), status.getLabel(), a.getWaitlistOrder(),
                a.getCreatedAt(), a.getProcessedAt(), a.getDecisionReason(),
                a.getCanceledAt(), a.getCancellationReason(),
                a.getCompletionStatus(), a.getCertificateNo(), a.getCertificateIssuedAt(),
                totalAttendanceCount, presentAttendanceCount, attendanceRate, earnedMileagePoints,
                remainingCapacity, a.getProgram().getRecruitmentEndsAt());
    }

    /**
     * 승인/반려 공통: 신청 건을 락을 걸어 조회하고, 요청 경로의 programId에 실제로 속하는지,
     * 그리고 아직 처리되지 않은 건(APPLIED/WAITLISTED)인지 확인한다.
     */
    private ProgramApplication findApplicationForUpdate(Integer programId, Integer applicationId) {
        ProgramApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        if (!application.getProgram().getProgramId().equals(programId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        String currentStatus = application.getApplicationStatus();
        if (!ApplicationStatus.APPLIED.name().equals(currentStatus)
                && !ApplicationStatus.WAITLISTED.name().equals(currentStatus)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        return application;
    }

    private ProgramApplicationDecisionResponseDTO applyDecision(
            ProgramApplication application, ApplicationStatus decision, String reason, Integer staffId) {
        /**
         * updateDecision()은 @Modifying(clearAutomatically = true)라 실행 즉시 영속성 컨텍스트를
         * clear한다. application.student는 LAZY이고 이 시점까지 한 번도 접근되지 않아 초기화되지
         * 않은 프록시이므로, clear 이후에 접근하면 LazyInitializationException으로 트랜잭션이
         * 롤백된다(방금 반영된 승인/반려 UPDATE까지 되돌아감). 그래서 update 전에 필요한 값을 전부
         * local variable로 미리 읽어두고, 이후에는 이 값들만 사용한다.
         */
        Integer applicationId = application.getApplicationId();
        Integer studentId = application.getStudent().getUserId();
        Integer programId = application.getProgram().getProgramId();
        String programName = application.getProgram().getProgramName();

        Instant now = Instant.now();
        applicationRepository.updateDecision(applicationId, decision.name(), reason, staffId, now);

        eventPublisher.publishEvent(new ApplicationDecidedEvent(
                applicationId, studentId, programId, programName, decision.name(), reason));

        return new ProgramApplicationDecisionResponseDTO(
                applicationId, programId, decision.name(), decision.getLabel(), reason, staffId, now);
    }

    /**
     * 취소 또는 반려로 정원 슬롯이 비었을 때, cancel()/reject()의 트랜잭션이 커밋된 이후에(AFTER_COMMIT)
     * 대기자 전원에게 "지금 몇 자리가 났는지"를 안내하는 알림을 보낸다. 커밋 전에 조회/발송하면 (1) 조회
     * 실패가 아직 반영되지 않은 취소/반려 처리까지 롤백시키거나, (2) NotificationSender.send()가
     * REQUIRES_NEW로 먼저 커밋해버려 이후 cancel()/reject() 트랜잭션이 실패했을 때 "취소/반려는 안
     * 됐는데 알림만 나간" 상태가 남을 수 있다. AFTER_COMMIT 시점엔 원본 트랜잭션이 이미 끝났으므로
     * 대기자 조회는 TransactionTemplate으로 REQUIRES_NEW 트랜잭션을 열어 collectWaitlistNotificationTargets()
     * 안에서만 수행한다(같은 클래스 안에서 이 메서드를 그냥 호출하면 프록시를 안 거쳐서 @Transactional이
     * 안 먹으므로, 애노테이션 대신 TransactionTemplate으로 직접 트랜잭션 경계를 만든다). 조회 트랜잭션이
     * 끝난 뒤(커밋 완료 후)에만 notificationSender.send()를 반복 호출한다 — 발송은 조회와 다른 관심사이고,
     * 지금은 NotificationSenderImpl이 DB 저장만 해서 빠르지만 나중에 실제 채널(SMS/카카오톡/이메일)이
     * 붙으면 느려질 수 있는 외부 I/O이므로, 그 시간만큼 트랜잭션(과 그 밑의 DB 커넥션)을 붙잡고 있을 이유가
     * 없다. 알림 발송 실패는 이미 커밋된 취소/반려 처리에 영향을 줄 수 없지만, 예외가 이벤트 리스너 밖으로
     * 전파되지 않도록 여기서 로그만 남기고 무시한다.
     *
     * 대기 1순위 한 명만 골라 알림을 보내던 이전 방식은, 취소 두 건이 거의 동시에 발생하면 두
     * 리스너 실행이 동시에 같은 1순위를 읽어 중복 알림을 보내고 실제 2번째로 열린 자리는 아무에게도
     * 안내되지 않는 경쟁 조건이 있었다. 그래서 "특정 한 명을 예약"하는 대신, 매번 그 시점의 열린
     * 자리 수(capacity - 정원을 차지하는 APPLIED/APPROVED 건수)를 다시 계산해 대기자 전원에게
     * 그 개수를 그대로 안내하는 방식으로 바꿨다 — 누구를 고를지 정하지 않으므로 동시 실행 자체가
     * 문제가 되지 않는다. 이 개수는 안내용 스냅샷일 뿐이며, 실제 정원 검증은 approve()가 프로그램
     * 행 락 + 승인 건수 확인으로 별도 보장한다.
     *
     * 클래스 레벨 @Transactional(기본 REQUIRED)이 이 메서드에도 그대로 적용되면 Spring이 부팅 시점에
     * "@TransactionalEventListener 메서드는 @Transactional을 REQUIRES_NEW 또는 NOT_SUPPORTED로만 쓸 수
     * 있다"며 예외를 던진다 — 실제 트랜잭션 경계는 아래 TransactionTemplate으로 직접 관리하므로,
     * 이 메서드 자체는 어떤 트랜잭션에도 참여하지 않도록 NOT_SUPPORTED로 명시적으로 뺀다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notifyAllWaitlistedApplicantsOfOpenSlots(WaitlistSlotOpenedEvent event) {
        TransactionTemplate requiresNewTransaction = new TransactionTemplate(transactionManager);
        requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        WaitlistNotificationTargets targets =
                requiresNewTransaction.execute(status -> collectWaitlistNotificationTargets(event));
        if (targets == null) {
            return;
        }

        for (WaitlistNotificationTarget target : targets.targets()) {
            try {
                notificationSender.send(new NotificationRequest(
                        target.studentUserId(),
                        NotificationType.WAITLIST_SLOT_OPENED,
                        ModuleCode.PROGRAM,
                        "대기중인 프로그램에 자리가 났습니다",
                        "'%s' 프로그램에 %d자리가 났습니다. 지원 확정을 원하시면 서둘러 확인해주세요."
                                .formatted(event.programName(), targets.availableSlots())
                ));
            } catch (Exception e) {
                log.warn("대기자 자리 발생 알림 발송 실패 (applicationId={}, programId={})",
                        target.applicationId(), event.programId(), e);
            }
        }
    }

    /**
     * notifyAllWaitlistedApplicantsOfOpenSlots가 REQUIRES_NEW 트랜잭션 안에서만 실행하는 조회 부분.
     * 트랜잭션 밖(발송 시점)에서 지연 로딩 예외 없이 쓸 수 있도록, 엔티티가 아니라 필요한 값(studentUserId 등)만
     * 뽑아서 반환한다.
     */
    private WaitlistNotificationTargets collectWaitlistNotificationTargets(WaitlistSlotOpenedEvent event) {
        Optional<ExtracurricularProgram> program = programRepository.findById(event.programId());
        if (program.isEmpty()) {
            return null;
        }

        long occupiedCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(
                event.programId(), List.of(ApplicationStatus.APPLIED.name(), ApplicationStatus.APPROVED.name()));
        long availableSlots = program.get().getCapacity() - occupiedCount;
        if (availableSlots <= 0) {
            return null;
        }

        List<WaitlistNotificationTarget> targets = applicationRepository
                .findAllByProgram_ProgramIdAndApplicationStatusOrderByWaitlistOrderAsc(
                        event.programId(), ApplicationStatus.WAITLISTED.name())
                .stream()
                .map(applicant -> new WaitlistNotificationTarget(
                        applicant.getApplicationId(), applicant.getStudent().getUserId()))
                .toList();
        return new WaitlistNotificationTargets(targets, availableSlots);
    }

    private record WaitlistNotificationTarget(Integer applicationId, Integer studentUserId) {
    }

    private record WaitlistNotificationTargets(List<WaitlistNotificationTarget> targets, long availableSlots) {
    }
}
