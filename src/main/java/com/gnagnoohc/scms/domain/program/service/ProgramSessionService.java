package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramSessionResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgramSessionService {

    private final ExtracurricularProgramRepository programRepository;
    private final ProgramSessionRepository sessionRepository;

    // 운영부서가 프로그램의 회차(교육 일정)를 등록한다. 매개변수 3개의 의미:
    //   programId : 회차를 등록할 프로그램의 PK (URL 경로에서 옴)
    //   request   : 등록할 회차 내용 (요청 바디에서 옴)
    //   staffId   : 지금 로그인해서 이 요청을 보낸 운영부서 담당자의 id (인증 정보에서 옴)
    public ProgramSessionResponseDTO registerSession(Integer programId, ProgramSessionRegisterRequestDTO request, Integer staffId) {
        if (!programRepository.existsById(programId)) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_FOUND);
        }

        Instant now = Instant.now();
        Integer sessionId;
        try {
            sessionId = sessionRepository.insertSession(
                    programId, request.sessionNo(), request.sessionName(),
                    request.startsAt(), request.endsAt(), request.location(), staffId, now);
        } catch (DataIntegrityViolationException e) {
            // uq_program_session_program_no 유니크 제약 위반 = 이미 존재하는 회차 번호.
            throw new BusinessException(ErrorCode.DUPLICATE_SESSION_NO);
        }

        return new ProgramSessionResponseDTO(
                sessionId, programId, request.sessionNo(), request.sessionName(),
                request.startsAt(), request.endsAt(), request.location());
    }

    public List<ProgramSessionResponseDTO> listSessions(Integer programId) {
        if (!programRepository.existsById(programId)) {
            throw new BusinessException(ErrorCode.PROGRAM_NOT_FOUND);
        }

        return sessionRepository.findByProgram_ProgramIdOrderBySessionNoAsc(programId)
                .stream()
                .map(ProgramSessionResponseDTO::from)
                .toList();
    }
}
