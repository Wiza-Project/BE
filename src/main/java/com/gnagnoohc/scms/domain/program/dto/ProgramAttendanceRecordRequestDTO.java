package com.gnagnoohc.scms.domain.program.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 비교과프로그램 "출석 기록" 요청 DTO. 운영부서가 특정 회차에 대해 학생 한 명의 출석 여부를 기록/정정할 때 사용한다.
public record ProgramAttendanceRecordRequestDTO(
        // 출석 상태 코드값. "PRESENT"(출석) 또는 "ABSENT"(결석).
        @NotNull String attendanceStatus,

        // 출석한 시간(분). 선택값.
        @PositiveOrZero Integer attendedMinutes,

        @Size(max = 500) String note
) {
}
