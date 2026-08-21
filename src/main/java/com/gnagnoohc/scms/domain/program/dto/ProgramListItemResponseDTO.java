package com.gnagnoohc.scms.domain.program.dto;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;

import java.time.Instant;

// 학생용 프로그램 목록 화면 카드 하나에 필요한 필드만 담는다.
public record ProgramListItemResponseDTO(
        Integer programId,
        String programName,
        String operatingUnitCodeName,
        String programTypeCodeName,
        String programStatus,
        String programStatusLabel,
        Integer capacity,
        Instant recruitmentStartsAt,
        Instant recruitmentEndsAt,
        Instant operationStartsAt,
        Instant operationEndsAt
) {
    public static ProgramListItemResponseDTO from(ExtracurricularProgram program) {
        return new ProgramListItemResponseDTO(
                program.getProgramId(),
                program.getProgramName(),
                program.getOperatingUnitCode().getCodeName(),
                program.getProgramTypeCode().getCodeName(),
                program.getProgramStatus().name(),
                program.getProgramStatus().getLabel(),
                program.getCapacity(),
                program.getRecruitmentStartsAt(),
                program.getRecruitmentEndsAt(),
                program.getOperationStartsAt(),
                program.getOperationEndsAt()
        );
    }
}
