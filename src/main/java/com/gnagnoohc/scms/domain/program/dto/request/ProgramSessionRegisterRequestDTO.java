package com.gnagnoohc.scms.domain.program.dto.request;

import com.gnagnoohc.scms.domain.program.entity.SessionLocationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// 비교과프로그램 "회차 등록" 요청 DTO. 운영부서가 프로그램의 회차(교육 일정)를 등록할 때 사용한다.
public record ProgramSessionRegisterRequestDTO(
        // 회차 번호. 같은 프로그램 안에서 중복될 수 없다(uq_program_session_program_no).
        @NotNull @Positive Integer sessionNo,

        // 회차명. 선택값(예: "1주차 오리엔테이션").
        @Size(max = 150) String sessionName,

        @NotNull Instant startsAt,
        @NotNull Instant endsAt,

        // 장소 입력 방식. DIRECT_INPUT이면 location이 필수이고, SAME_AS_PREVIOUS이면
        // location을 비워 보내도 되며 서버가 직전 회차의 장소를 그대로 복사한다.
        @NotNull SessionLocationType locationType,
        @Size(max = 300) String location
) {
}
