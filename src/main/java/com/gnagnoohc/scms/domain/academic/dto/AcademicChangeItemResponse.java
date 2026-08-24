package com.gnagnoohc.scms.domain.academic.dto;

import com.gnagnoohc.scms.domain.academic.entity.StudentAcademicChange;

import java.time.LocalDate;

/**
 * 학적변동목록(화면 1) / 변동이력 탭(화면 2) 공용 행 하나.
 * {@code no}는 오래된 변동부터 매기는 순번 — 조회 시 조립한다
 */
public record AcademicChangeItemResponse(
        int no,
        LocalDate changeDate,
        String changeTypeCode,
        String changeTypeName,
        String changeReasonCode,
        String changeReasonName,
        String militaryStatus,
        Integer scheduledReturnYear,
        String scheduledReturnSemesterCode,
        String note
) {
    public static AcademicChangeItemResponse of(int no, StudentAcademicChange change) {
        return new AcademicChangeItemResponse(
                no,
                change.getChangeDate(),
                change.getChangeTypeCode().getCode(),
                change.getChangeTypeCode().getCodeName(),
                change.getChangeReasonCode() != null ? change.getChangeReasonCode().getCode() : null,
                change.getChangeReasonCode() != null ? change.getChangeReasonCode().getCodeName() : null,
                change.getMilitaryStatus(),
                change.getScheduledReturnYear() != null ? change.getScheduledReturnYear().intValue() : null,
                change.getScheduledReturnSemesterCode(),
                change.getNote()
        );
    }
}
