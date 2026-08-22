package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;

import java.math.BigDecimal;
import java.time.Instant;

// 학생용 프로그램 목록 화면 카드 하나에 필요한 필드만 담는다.
public record ProgramListItemResponseDTO(
        Integer programId,
        String programName,
        String operatingUnitCodeName,
        String programTypeCodeName,
        String competencyName,
        String programStatus,
        String programStatusLabel,
        Integer capacity,
        long applicantCount,
        int remainingCapacity,
        Instant recruitmentStartsAt,
        Instant recruitmentEndsAt,
        Instant operationStartsAt,
        Instant operationEndsAt,
        BigDecimal mileagePoints
) {
    /**
     * 엔티티와 (별도로 집계된) 신청자 수를 조합해 목록 카드용 DTO를 만든다.
     * applicantCount를 파라미터로 받는 이유는 신청자 수가 별도 COUNT 쿼리로 집계되어 엔티티 자체에는 없기 때문이다.
     */
    public static ProgramListItemResponseDTO from(ExtracurricularProgram program, long applicantCount) {
        return new ProgramListItemResponseDTO(
                program.getProgramId(),
                program.getProgramName(),
                program.getOperatingUnitCode().getCodeName(),
                program.getProgramTypeCode().getCodeName(),
                program.getCompetency().getCompetencyName(),
                program.getProgramStatus().name(),
                program.getProgramStatus().getLabel(),
                program.getCapacity(),
                applicantCount,
                Math.max(program.getCapacity() - (int) applicantCount, 0),
                program.getRecruitmentStartsAt(),
                program.getRecruitmentEndsAt(),
                program.getOperationStartsAt(),
                program.getOperationEndsAt(),
                program.getMileagePolicy() != null ? program.getMileagePolicy().getPoints() : null
        );
    }
}
