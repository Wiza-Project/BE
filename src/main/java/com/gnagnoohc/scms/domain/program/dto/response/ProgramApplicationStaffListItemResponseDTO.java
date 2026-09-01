package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.service.ApplicationStatus;

import java.time.Instant;

/**
 * 스태프용 "프로그램 신청자 목록" 화면 한 줄에 필요한 필드를 담는다. 학생용 ProgramApplicationSummaryResponseDTO와
 * 거의 같은 정보이지만, 스태프는 "누구의" 신청인지(학생 이름/학번)를 알아야 하므로 student 정보를 추가로 담는다.
 */
public record ProgramApplicationStaffListItemResponseDTO(
        Integer applicationId,
        Integer studentId,
        String studentName,
        String studentNo,
        String applicationStatus,
        String applicationStatusLabel,
        Integer waitlistOrder,
        Instant appliedAt,
        Instant processedAt,
        String completionStatus,
        String certificateNo
) {
    public static ProgramApplicationStaffListItemResponseDTO from(ProgramApplication a) {
        ApplicationStatus status = ApplicationStatus.valueOf(a.getApplicationStatus());
        return new ProgramApplicationStaffListItemResponseDTO(
                a.getApplicationId(),
                a.getStudent().getUserId(),
                a.getStudent().getUserName(),
                a.getStudent().getUniversityNo(),
                a.getApplicationStatus(),
                status.getLabel(),
                a.getWaitlistOrder(),
                a.getCreatedAt(),
                a.getProcessedAt(),
                a.getCompletionStatus(),
                a.getCertificateNo()
        );
    }
}
