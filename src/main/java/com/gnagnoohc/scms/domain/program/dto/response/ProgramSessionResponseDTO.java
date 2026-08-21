package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ProgramSession;

import java.time.Instant;

// 비교과프로그램 회차 응답 DTO. 등록 응답과 목록 조회 응답에 공통으로 사용한다.
public record ProgramSessionResponseDTO(
        Integer programSessionId,
        Integer programId,
        Integer sessionNo,
        String sessionName,
        Instant startsAt,
        Instant endsAt,
        String location
) {
    public static ProgramSessionResponseDTO from(ProgramSession session) {
        return new ProgramSessionResponseDTO(
                session.getProgramSessionId(),
                session.getProgram().getProgramId(),
                session.getSessionNo(),
                session.getSessionName(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getLocation()
        );
    }
}
