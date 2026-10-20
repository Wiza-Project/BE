package com.gnagnoohc.scms.domain.program.dto.program;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.service.ApplicationStatus;

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
        BigDecimal mileagePoints,
        // 로그인한 학생 본인의 이 프로그램에 대한 신청 상태. 신청 이력이 없으면(또는 취소해 재신청 가능하면) null.
        String myApplicationStatus,
        String myApplicationStatusLabel
) {
    /**
     * 엔티티와 (별도로 집계된) 신청자 수, 로그인 학생 본인의 신청 상태를 조합해 목록 카드용 DTO를 만든다.
     * applicantCount를 파라미터로 받는 이유는 신청자 수가 별도 COUNT 쿼리로 집계되어 엔티티 자체에는 없기 때문이다.
     * myApplicationStatus가 null이면(신청 이력 없음, 또는 취소되어 재신청 가능함) 라벨도 null로 내려간다.
     */
    public static ProgramListItemResponseDTO from(ExtracurricularProgram program, long applicantCount,
                                                    String myApplicationStatus) {
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
                program.getMileagePolicy() != null ? program.getMileagePolicy().getPoints() : null,
                myApplicationStatus,
                myApplicationStatus != null ? ApplicationStatus.valueOf(myApplicationStatus).getLabel() : null
        );
    }
}
