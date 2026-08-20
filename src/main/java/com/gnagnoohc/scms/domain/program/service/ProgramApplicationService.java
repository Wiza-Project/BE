package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.ProgramApplicationCancelResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramApplicationDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramApplyResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramApplicationService {

    private final ExtracurricularProgramRepository programRepository;
    private final ProgramApplicationRepository applicationRepository;

    // 학생의 프로그램 참여 신청을 접수한다. 매개변수 2개의 의미:
    //   programId : 신청할 프로그램의 PK (URL 경로에서 옴)
    //   studentId : 지금 로그인해서 이 요청을 보낸 학생의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
    public ProgramApplyResponseDTO apply(Integer programId, Integer studentId) {

        // (a) 존재 확인 + 락 --------------------------------------------------------
        // findByIdForUpdate는 이 프로그램 row에 비관적 락을 걸어서, 아래 (c) 정원 계산과
        // (d) INSERT 사이에 다른 신청 요청이 끼어들어 순번이 꼬이는 것을 막는다.
        ExtracurricularProgram program = programRepository.findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_NOT_FOUND));

        // (b) 신청 가능 기간인지 확인 --------------------------------------------------
        // ProgramService.update/delete와 같은 이유로, programStatus 컬럼(스케줄러가 최대 1분 지연으로 갱신)을
        // 믿지 않고 지금 시각을 모집 시작/종료 시각과 직접 비교한다.
        Instant now = Instant.now();
        if (now.isBefore(program.getRecruitmentStartsAt()) || !now.isBefore(program.getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.APPLICATION_PERIOD_CLOSED);
        }

        // (c) 정원 확인 및 상태 결정 ----------------------------------------------------
        // 정원 내 신청("APPLIED") 건수가 아직 정원(capacity)보다 적으면 정원 내 신청으로,
        // 그렇지 않으면 대기 신청("WAITLISTED")으로 접수하고 다음 대기순번을 매긴다.
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

        // (d) 실제 DB 반영 -------------------------------------------------------------
        Integer applicationId;
        try {
            applicationId = applicationRepository.insertApplication(
                    programId, studentId, status.name(), waitlistOrder, now);
        } catch (DataIntegrityViolationException e) {
            // uq_program_application_program_student 유니크 제약 위반 = 이미 신청한 프로그램.
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }

        return new ProgramApplyResponseDTO(
                applicationId, programId, status.name(), status.getLabel(), waitlistOrder, now);
    }

    // 운영부서가 신청 건을 승인한다. 정원(capacity) 내에서만 승인할 수 있다 —
    // 신청 시점에 대기(WAITLISTED)로 분류됐던 건이라도, 다른 신청이 반려되어 자리가 나면 승인할 수 있다.
    public ProgramApplicationDecisionResponseDTO approve(Integer programId, Integer applicationId, Integer staffId) {
        ProgramApplication application = findApplicationForUpdate(programId, applicationId);

        // 프로그램 row에 락을 걸어, "현재 승인 건수 확인"과 "실제 승인 반영" 사이에
        // 다른 승인 요청이 끼어들어 정원을 초과하는 경쟁 조건을 막는다 (apply()와 동일한 이유).
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

    // 학생이 스스로 자신의 참여 신청을 취소한다. 모집 기간이 끝나지 않은 경우에만 취소할 수 있다.
    //   studentId : 지금 로그인해서 이 요청을 보낸 학생의 id (인증 정보에서 옴, 클라이언트가 위조 불가)
    public ProgramApplicationCancelResponseDTO cancel(Integer programId, Integer applicationId, Integer studentId, String reason) {

        // (a) 존재 확인 + 락 --------------------------------------------------------
        ProgramApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        // (b) 요청 경로의 programId, 로그인한 학생 본인 소유가 맞는지 확인 -----------------------
        // 존재 여부를 굳이 구분해서 노출하지 않기 위해, 승인/반려의 programId 불일치 처리와 같은 방식으로
        // 두 경우 모두 APPLICATION_NOT_FOUND를 던진다.
        if (!application.getProgram().getProgramId().equals(programId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        if (!application.getStudent().getUserId().equals(studentId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // (c) 이미 반려/취소된 건이 아닌지 확인 --------------------------------------------
        // 취소는 APPLIED/WAITLISTED/APPROVED 상태에서만 가능하다 (REJECTED/CANCELLED는 이미 종결된 상태).
        String currentStatus = application.getApplicationStatus();
        if (!ApplicationStatus.APPLIED.name().equals(currentStatus)
                && !ApplicationStatus.WAITLISTED.name().equals(currentStatus)
                && !ApplicationStatus.APPROVED.name().equals(currentStatus)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_CANCELED);
        }

        // (d) 모집 기간이 끝나지 않았는지 확인 ------------------------------------------------
        // apply()와 같은 이유로, programStatus 컬럼을 믿지 않고 지금 시각을 모집 종료 시각과 직접 비교한다.
        Instant now = Instant.now();
        if (!now.isBefore(application.getProgram().getRecruitmentEndsAt())) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        // (e) 실제 DB 반영 -------------------------------------------------------------
        // updateCancellation의 WHERE 절에도 (c)/(d)와 같은 조건이 걸려있어, 이 확인과 실제 UPDATE 사이의
        // 경쟁 조건으로 0개의 row만 바뀌었다면 역시 "지금은 취소할 수 없는 상태였다"는 뜻이다
        // (ProgramService.delete의 deletedRows == 0 처리와 동일한 이유).
        int updatedRows = applicationRepository.updateCancellation(applicationId, reason, now);
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        return new ProgramApplicationCancelResponseDTO(
                applicationId, programId, ApplicationStatus.CANCELLED.name(), ApplicationStatus.CANCELLED.getLabel(),
                reason, now);
    }

    // 승인/반려 공통: 신청 건을 락을 걸어 조회하고, 요청 경로의 programId에 실제로 속하는지,
    // 그리고 아직 처리되지 않은 건(APPLIED/WAITLISTED)인지 확인한다.
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

        return new ProgramApplicationDecisionResponseDTO(
                application.getApplicationId(), application.getProgram().getProgramId(),
                decision.name(), decision.getLabel(), reason, staffId, now);
    }
}
