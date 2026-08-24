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
import com.gnagnoohc.scms.domain.program.event.WaitlistSlotOpenedEvent;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramAttendanceRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramMileageTransactionRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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

    /**
     * 학생의 프로그램 참여 신청을 접수한다. 매개변수 2개의 의미:
     *   programId : 신청할 프로그램의 PK (URL 경로에서 옴)
     *   studentId : 지금 로그인해서 이 요청을 보낸 학생의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
     */
    public ProgramApplyResponseDTO apply(Integer programId, Integer studentId) {

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
         * 믿지 않고 지금 시각을 모집 시작/종료 시각과 직접 비교한다.
         */
        Instant now = Instant.now();
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
         * 정원 내 신청("APPLIED") 건수가 아직 정원(capacity)보다 적으면 정원 내 신청으로,
         * 그렇지 않으면 대기 신청("WAITLISTED")으로 접수하고 다음 대기순번을 매긴다.
         */
        long appliedCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatus(
                programId, ApplicationStatus.APPLIED.name());

        ApplicationStatus status;
        Integer waitlistOrder;
        if (appliedCount < program.getCapacity()) {
            status = ApplicationStatus.APPLIED;
            waitlistOrder = null;
        } else {
            status = ApplicationStatus.WAITLISTED;
            waitlistOrder = applicationRepository.findMaxWaitlistOrderByProgramId(programId) + 1;
        }

        // (e) 실제 DB 반영 -------------------------------------------------------------
        Integer applicationId;
        if (existing.isPresent()) {
            // 취소됐던 기존 row를 새 신청으로 되살린다. WHERE 절이 여전히 CANCELLED인지 재확인하므로,
            // 0건이면 그 사이 다른 요청이 먼저 처리한 것이니 "이미 신청됨"으로 처리한다.
            int updatedRows = applicationRepository.reviveApplication(
                    existing.get().getApplicationId(), status.name(), waitlistOrder, now);
            if (updatedRows == 0) {
                throw new BusinessException(ErrorCode.ALREADY_APPLIED);
            }
            applicationId = existing.get().getApplicationId();
        } else {
            try {
                applicationId = applicationRepository.insertApplication(
                        programId, studentId, status.name(), waitlistOrder, false, now);
            } catch (DataIntegrityViolationException e) {
                // uq_program_application_program_student 유니크 제약 위반 = 이미 신청한 프로그램(동시 요청 경쟁).
                throw new BusinessException(ErrorCode.ALREADY_APPLIED);
            }
        }

        return new ProgramApplyResponseDTO(
                applicationId, programId, status.name(), status.getLabel(), waitlistOrder, now);
    }

    /**
     * 운영부서가 신청 건을 승인한다. 정원(capacity) 내에서만 승인할 수 있다 —
     * 신청 시점에 대기(WAITLISTED)로 분류됐던 건이라도, 다른 신청이 반려되어 자리가 나면 승인할 수 있다.
     */
    public ProgramApplicationDecisionResponseDTO approve(Integer programId, Integer applicationId, Integer staffId) {
        ProgramApplication application = findApplicationForUpdate(programId, applicationId);

        /**
         * 프로그램 row에 락을 걸어, "현재 승인 건수 확인"과 "실제 승인 반영" 사이에
         * 다른 승인 요청이 끼어들어 정원을 초과하는 경쟁 조건을 막는다 (apply()와 동일한 이유).
         */
        ExtracurricularProgram program = programRepository.findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        long approvedCount = applicationRepository.countByProgram_ProgramIdAndApplicationStatus(
                programId, ApplicationStatus.APPROVED.name());
        if (approvedCount >= program.getCapacity()) {
            throw new BusinessException(ErrorCode.PROGRAM_CAPACITY_EXCEEDED);
        }

        return applyDecision(application, ApplicationStatus.APPROVED, null, staffId);
    }

    // 운영부서가 신청 건을 반려한다. 반려 사유(reason)는 컨트롤러 단 @NotBlank 검증으로 항상 채워져 있다.
    public ProgramApplicationDecisionResponseDTO reject(Integer programId, Integer applicationId, String reason, Integer staffId) {
        ProgramApplication application = findApplicationForUpdate(programId, applicationId);
        return applyDecision(application, ApplicationStatus.REJECTED, reason, staffId);
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

        // (a) 존재 확인 + 락 --------------------------------------------------------
        ProgramApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        /**
         * (b) 요청 경로의 programId, 로그인한 학생 본인 소유가 맞는지 확인 -----------------------
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
         * (c) 이미 반려/취소된 건이 아닌지 확인 --------------------------------------------
         * 취소는 APPLIED/WAITLISTED/APPROVED 상태에서만 가능하다 (REJECTED/CANCELLED는 이미 종결된 상태).
         */
        String currentStatus = application.getApplicationStatus();
        if (!ApplicationStatus.APPLIED.name().equals(currentStatus)
                && !ApplicationStatus.WAITLISTED.name().equals(currentStatus)
                && !ApplicationStatus.APPROVED.name().equals(currentStatus)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_CANCELED);
        }

        /**
         * (d) 모집 기간이 끝나지 않았는지 확인 ------------------------------------------------
         * apply()와 같은 이유로, programStatus 컬럼을 믿지 않고 지금 시각을 모집 종료 시각과 직접 비교한다.
         */
        Instant now = Instant.now();
        if (!now.isBefore(application.getProgram().getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        /**
         * (e) 실제 DB 반영 -------------------------------------------------------------
         * updateCancellation의 WHERE 절에도 (c)/(d)와 같은 조건이 걸려있어, 이 확인과 실제 UPDATE 사이의
         * 경쟁 조건으로 0개의 row만 바뀌었다면 역시 "지금은 취소할 수 없는 상태였다"는 뜻이다
         * (ProgramService.delete의 deletedRows == 0 처리와 동일한 이유).
         */
        int updatedRows = applicationRepository.updateCancellation(applicationId, reason, now);
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        /**
         * (f) 정원 슬롯이 실제로 비었다면 대기 1순위에게 알림 ------------------------------------
         * WAITLISTED는 애초에 정원 외였으므로, 그 상태였던 신청이 취소된 경우는 자리 발생이 아니다.
         * APPLIED/APPROVED만 정원을 차지하던 상태이므로 그 경우에만 대기자에게 알린다.
         */
        if (ApplicationStatus.APPLIED.name().equals(currentStatus)
                || ApplicationStatus.APPROVED.name().equals(currentStatus)) {
            eventPublisher.publishEvent(
                    new WaitlistSlotOpenedEvent(programId, application.getProgram().getProgramName()));
        }

        return new ProgramApplicationCancelResponseDTO(
                applicationId, programId, ApplicationStatus.CANCELLED.name(), ApplicationStatus.CANCELLED.getLabel(),
                reason, now);
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
        return PageResponse.from(applications.map(a -> toSummary(
                a, attendanceByApplication.get(a.getApplicationId()), earnedPointsByApplication.get(a.getApplicationId()))));
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
            BigDecimal earnedMileagePoints) {
        ApplicationStatus status = ApplicationStatus.valueOf(a.getApplicationStatus());
        int totalAttendanceCount = attendance != null ? attendance.getTotalCount().intValue() : 0;
        int presentAttendanceCount = attendance != null ? attendance.getPresentCount().intValue() : 0;
        Double attendanceRate = totalAttendanceCount > 0
                ? presentAttendanceCount * 100.0 / totalAttendanceCount
                : null;
        return new ProgramApplicationSummaryResponseDTO(
                a.getApplicationId(), a.getProgram().getProgramId(), a.getProgram().getProgramName(),
                a.getApplicationStatus(), status.getLabel(), a.getWaitlistOrder(),
                a.getCreatedAt(), a.getProcessedAt(), a.getDecisionReason(),
                a.getCanceledAt(), a.getCancellationReason(),
                a.getCompletionStatus(), a.getCertificateNo(), a.getCertificateIssuedAt(),
                totalAttendanceCount, presentAttendanceCount, attendanceRate, earnedMileagePoints);
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
        Instant now = Instant.now();
        applicationRepository.updateDecision(
                application.getApplicationId(), decision.name(), reason, staffId, now);

        /**
         * TODO: 알림 발송 연동 (공통 담당자 구현 예정, global.common.entity.Notification 사용) — 승인/반려
         *   (대기자였다가 여기서 승인되어 "승격"되는 경우 포함) 결과를 학생에게 카카오톡/이메일로 알려야 한다.
         *   지금은 Notification 엔티티만 있고 실제 발송 서비스가 없어 program 도메인에서는 손대지 않는다.
         */

        return new ProgramApplicationDecisionResponseDTO(
                application.getApplicationId(), application.getProgram().getProgramId(),
                decision.name(), decision.getLabel(), reason, staffId, now);
    }

    /**
     * 취소로 정원 슬롯이 비었을 때, cancel()의 트랜잭션이 커밋된 이후에(AFTER_COMMIT) 대기 1순위
     * 학생을 조회해 "자리가 났다"는 알림을 보낸다. 커밋 전에 조회/발송하면 (1) 조회 실패가 아직
     * 반영되지 않은 취소 처리까지 롤백시키거나, (2) NotificationSender.send()가 REQUIRES_NEW로
     * 먼저 커밋해버려 이후 cancel() 트랜잭션이 실패했을 때 "취소는 안 됐는데 알림만 나간" 상태가
     * 남을 수 있다. 클래스 레벨 @Transactional의 프록시가 가로챌 수 있도록 public이어야 한다.
     * 알림 발송 실패는 이미 커밋된 취소 처리에 영향을 줄 수 없지만, 예외가 이벤트 리스너 밖으로
     * 전파되지 않도록 여기서 로그만 남기고 무시한다. AFTER_COMMIT 시점엔 원본 트랜잭션이 이미
     * 끝났으므로 대기자 조회를 위해 REQUIRES_NEW로 새 트랜잭션을 연다(클래스 레벨 REQUIRED로는
     * @TransactionalEventListener와 함께 쓸 수 없어 부팅 시 예외가 난다).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyNextWaitlistedApplicant(WaitlistSlotOpenedEvent event) {
        applicationRepository.findFirstByProgram_ProgramIdAndApplicationStatusOrderByWaitlistOrderAsc(
                        event.programId(), ApplicationStatus.WAITLISTED.name())
                .ifPresent(nextInLine -> {
                    try {
                        notificationSender.send(new NotificationRequest(
                                nextInLine.getStudent().getUserId(),
                                NotificationType.WAITLIST_SLOT_OPENED,
                                ModuleCode.PROGRAM,
                                "대기중인 프로그램에 자리가 났습니다",
                                "'%s' 프로그램에 자리가 생겼습니다. 지원 확정을 원하시면 서둘러 확인해주세요."
                                        .formatted(event.programName())
                        ));
                    } catch (Exception e) {
                        log.warn("대기자 자리 발생 알림 발송 실패 (applicationId={}, programId={})",
                                nextInLine.getApplicationId(), event.programId(), e);
                    }
                });
    }
}
