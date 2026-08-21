package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;

import java.math.BigDecimal;
import java.time.Instant;

/** 학생용 프로그램 목록 화면 카드 하나에 필요한 필드만 담는다. */
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
        /** 이수 시 적립되는 마일리지 점수. 마일리지 정책이 연결되지 않은 프로그램이면 null. */
        BigDecimal mileagePoints
) {
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
