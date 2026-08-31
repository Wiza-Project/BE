package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.service.ApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// 학생용 프로그램 상세 화면에 필요한 기본정보 전체 + 회차 목록 + 신청자 수를 담는다.
public record ProgramDetailResponseDTO(
        Integer programId,
        String programName,
        String description,
        Integer operatingUnitCodeId,
        String operatingUnitCodeName,
        Integer programTypeCodeId,
        String programTypeCodeName,
        Integer competencyId,
        String competencyName,
        String managerUserName,
        Integer fileGroupId,
        String fileName,
        Integer mileagePolicyId,
        BigDecimal mileagePoints,
        String programStatus,
        String programStatusLabel,
        Integer capacity,
        long applicantCount,
        int remainingCapacity,
        Instant recruitmentStartsAt,
        Instant recruitmentEndsAt,
        Instant operationStartsAt,
        Instant operationEndsAt,
        Instant createdAt,
        List<ProgramSessionResponseDTO> sessions,
        // 로그인한 학생 본인의 이 프로그램에 대한 신청 상태. 신청 이력이 없으면(또는 취소해 재신청 가능하면) null.
        String myApplicationStatus,
        String myApplicationStatusLabel
) {
    public static ProgramDetailResponseDTO from(ExtracurricularProgram program, long applicantCount,
                                                 List<ProgramSessionResponseDTO> sessions,
                                                 String myApplicationStatus, String fileName) {
        return new ProgramDetailResponseDTO(
                program.getProgramId(),
                program.getProgramName(),
                program.getDescription(),
                program.getOperatingUnitCode().getCodeId(),
                program.getOperatingUnitCode().getCodeName(),
                program.getProgramTypeCode().getCodeId(),
                program.getProgramTypeCode().getCodeName(),
                program.getCompetency().getCompetencyId(),
                program.getCompetency().getCompetencyName(),
                program.getManagerUser().getUserName(),
                program.getFileGroup() != null ? program.getFileGroup().getFileGroupId() : null,
                fileName,
                program.getMileagePolicy() != null ? program.getMileagePolicy().getMileagePolicyId() : null,
                program.getMileagePolicy() != null ? program.getMileagePolicy().getPoints() : null,
                program.getProgramStatus().name(),
                program.getProgramStatus().getLabel(),
                program.getCapacity(),
                applicantCount,
                Math.max(program.getCapacity() - (int) applicantCount, 0),
                program.getRecruitmentStartsAt(),
                program.getRecruitmentEndsAt(),
                program.getOperationStartsAt(),
                program.getOperationEndsAt(),
                program.getCreatedAt(),
                sessions,
                myApplicationStatus,
                myApplicationStatus != null ? ApplicationStatus.valueOf(myApplicationStatus).getLabel() : null
        );
    }
}
