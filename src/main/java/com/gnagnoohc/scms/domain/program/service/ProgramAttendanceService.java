package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramAttendanceRecordRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAttendanceQrTokenResponseDTO;
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
    private final ProgramAttendanceQrTokenService qrTokenService;

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

    // 운영부서가 특정 회차용 QR 출석체크 토큰을 발급한다. 스태프 화면은 이 token 문자열을 그대로 QR 이미지로
    // 렌더링해서 강의실 화면/프로젝터에 띄우고, 학생들이 각자 폰으로 스캔해 checkInWithQr를 호출하게 한다.
    public ProgramAttendanceQrTokenResponseDTO issueQrToken(Integer programId, Integer sessionId) {
        ProgramSession session = sessionRepository.findByProgramSessionIdAndProgram_ProgramId(sessionId, programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_SESSION_NOT_FOUND));

        ProgramAttendanceQrTokenService.IssuedToken issued =
                qrTokenService.issue(programId, session.getProgramSessionId());
        return new ProgramAttendanceQrTokenResponseDTO(issued.token(), issued.expiresAt());
    }

    // 학생이 본인 폰으로 QR을 스캔해 스스로 출석 체크인한다. recordAttendance(스태프 수동 입력)와 같은 테이블에
    // 같은 방식(upsert)으로 기록하되, recordedBy에는 스태프가 아니라 체크인한 학생 본인의 id가 들어간다 —
    // 이후 "누가 기록했는지"를 보면 QR 자기체크인인지 스태프가 수동으로 정정한 것인지 구분할 수 있다.
    // 두 경로가 같은 테이블을 공유하므로, QR로 먼저 체크인한 뒤 스태프가 나중에 수동으로 정정하는 것도 그대로 가능하다.
    public ProgramAttendanceResponseDTO checkInWithQr(Integer programId, Integer sessionId, String token, Integer studentId) {
        ProgramSession session = sessionRepository.findByProgramSessionIdAndProgram_ProgramId(sessionId, programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROGRAM_SESSION_NOT_FOUND));

        qrTokenService.verify(token, programId, session.getProgramSessionId());

        // 이 학생이 이 프로그램에 낸 신청 건을 찾는다. recordAttendance와 달리 applicationId를 URL로 받지 않고
        // (학생이 자기 applicationId를 알 필요가 없게) programId + 로그인한 학생 id로 직접 찾는다.
        ProgramApplication application = applicationRepository
                .findByProgram_ProgramIdAndStudent_UserId(programId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        // recordAttendance와 같은 이유로, 승인(APPROVED)된 신청 건만 출석을 기록할 수 있다.
        if (!ApplicationStatus.APPROVED.name().equals(application.getApplicationStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_APPROVED);
        }

        attendanceRepository.upsertAttendance(
                application.getApplicationId(), session.getProgramSessionId(), AttendanceStatus.PRESENT.name(),
                null, null, studentId, Instant.now());

        return attendanceRepository.findByApplication_ApplicationIdAndProgramSession_ProgramSessionId(
                        application.getApplicationId(), session.getProgramSessionId())
                .map(ProgramAttendanceResponseDTO::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    private AttendanceStatus parseAttendanceStatus(String value) {
        try {
            return AttendanceStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출석 상태는 PRESENT 또는 ABSENT여야 합니다.");
        }
    }
}
