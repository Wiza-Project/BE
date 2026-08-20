package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramAttendanceRecordRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAttendanceResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramAttendanceRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramAttendanceService {

    private final ProgramSessionRepository sessionRepository;
    private final ProgramApplicationRepository applicationRepository;
    private final ProgramAttendanceRepository attendanceRepository;

    // 운영부서가 특정 회차에 대해 학생 한 명의 출석 여부를 기록(이미 기록이 있으면 정정)한다. 매개변수 5개의 의미:
    //   programId   : 회차가 속한 프로그램의 PK (URL 경로에서 옴)
    //   sessionId   : 출석을 기록할 회차의 PK (URL 경로에서 옴)
    //   applicationId : 출석 대상 학생의 신청 건 PK (URL 경로에서 옴)
    //   request     : 출석 상태 등 기록할 내용 (요청 바디에서 옴)
    //   staffId     : 지금 로그인해서 이 요청을 보낸 운영부서 담당자의 id (인증 정보에서 옴)
    public ProgramAttendanceResponseDTO recordAttendance(
            Integer programId, Integer sessionId, Integer applicationId,
            ProgramAttendanceRecordRequestDTO request, Integer staffId) {

        ProgramSession session = sessionRepository.findByProgramSessionIdAndProgram_ProgramId(sessionId, programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_SESSION_NOT_FOUND));

        ProgramApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (!application.getProgram().getProgramId().equals(programId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        // 승인(APPROVED)되어 실제로 참여 중인 학생에 대해서만 출석을 기록할 수 있다 —
        // 대기/반려 건까지 출석을 기록하면 이수 판정(judgeCompletion) 대상이 아닌 데이터가 쌓이게 된다.
        if (!ApplicationStatus.APPROVED.name().equals(application.getApplicationStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_APPROVED);
        }

        AttendanceStatus attendanceStatus = parseAttendanceStatus(request.attendanceStatus());

        attendanceRepository.upsertAttendance(
                applicationId, session.getProgramSessionId(), attendanceStatus.name(),
                request.attendedMinutes(), request.note(), staffId, Instant.now());

        return attendanceRepository.findByApplication_ApplicationIdAndProgramSession_ProgramSessionId(
                        applicationId, session.getProgramSessionId())
                .map(ProgramAttendanceResponseDTO::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    public List<ProgramAttendanceResponseDTO> listAttendance(Integer programId, Integer sessionId) {
        ProgramSession session = sessionRepository.findByProgramSessionIdAndProgram_ProgramId(sessionId, programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_SESSION_NOT_FOUND));

        return attendanceRepository.findByProgramSession_ProgramSessionId(session.getProgramSessionId())
                .stream()
                .map(ProgramAttendanceResponseDTO::from)
                .toList();
    }

    private AttendanceStatus parseAttendanceStatus(String value) {
        try {
            return AttendanceStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출석 상태는 PRESENT 또는 ABSENT여야 합니다.");
        }
    }
}
