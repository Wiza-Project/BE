package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;

import java.math.BigDecimal;
import java.time.Instant;

/** staff용 프로그램 목록 화면(본인 담당 프로그램만) 카드 하나에 필요한 필드를 담는다. */
public record ProgramAdminListItemResponseDTO(
        Integer programId,
        String programName,
        String operatingUnitCodeName,
        String programTypeCodeName,
        String programStatus,
        String programStatusLabel,
        Integer capacity,
        BigDecimal completionRate,
        long applicantCount,
        int remainingCapacity,
        Instant recruitmentStartsAt,
        Instant recruitmentEndsAt,
        Instant operationStartsAt,
        Instant operationEndsAt,
        Instant createdAt
) {
    public static ProgramAdminListItemResponseDTO from(ExtracurricularProgram program, long applicantCount) {
        return new ProgramAdminListItemResponseDTO(
                program.getProgramId(),
                program.getProgramName(),
                program.getOperatingUnitCode().getCodeName(),
                program.getProgramTypeCode().getCodeName(),
                program.getProgramStatus().name(),
                program.getProgramStatus().getLabel(),
                program.getCapacity(),
                program.getCompletionRate(),
                applicantCount,
                Math.max(program.getCapacity() - (int) applicantCount, 0),
                program.getRecruitmentStartsAt(),
                program.getRecruitmentEndsAt(),
                program.getOperationStartsAt(),
                program.getOperationEndsAt(),
                program.getCreatedAt()
        );
    }
}
