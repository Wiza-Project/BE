package com.gnagnoohc.scms.domain.program.dto.application;

/**
 * 비교과프로그램 참여 신청 "취소" 요청 DTO. 취소는 학생 본인의 의사이므로 사유는 선택 입력이며,
 * 요청 바디 자체를 아예 생략해도(@RequestBody(required = false)) 된다.
 */
public record ProgramApplicationCancelRequestDTO(
        String reason
) {
}
