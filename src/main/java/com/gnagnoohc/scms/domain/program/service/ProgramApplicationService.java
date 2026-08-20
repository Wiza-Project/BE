package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.ProgramApplyResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ApplicationStatus;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
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
}
