package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// staff용 프로그램 상세 화면(본인 담당 프로그램만)에 필요한 기본정보 전체 + 회차 목록 + 수정/삭제 가능 여부를 담는다.
public record ProgramAdminDetailResponseDTO(
        Integer programId,
        String programName,
        String description,
        String operatingUnitCodeName,
        String programTypeCodeName,
        String competencyName,
        String managerUserName,
        Integer fileGroupId,
        Integer mileagePolicyId,
        BigDecimal mileagePoints,
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
        Instant createdAt,
        List<ProgramSessionResponseDTO> sessions,
        boolean isEditable,
        boolean isDeletable
) {
    public static ProgramAdminDetailResponseDTO from(ExtracurricularProgram program, long applicantCount,
                                                       List<ProgramSessionResponseDTO> sessions, boolean editable) {
        return new ProgramAdminDetailResponseDTO(
                program.getProgramId(),
                program.getProgramName(),
                program.getDescription(),
                program.getOperatingUnitCode().getCodeName(),
                program.getProgramTypeCode().getCodeName(),
                program.getCompetency().getCompetencyName(),
                program.getManagerUser().getUserName(),
                program.getFileGroup() != null ? program.getFileGroup().getFileGroupId() : null,
                program.getMileagePolicy() != null ? program.getMileagePolicy().getMileagePolicyId() : null,
                program.getMileagePolicy() != null ? program.getMileagePolicy().getPoints() : null,
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
                program.getCreatedAt(),
                sessions,
                editable,
                editable
        );
    }
}
