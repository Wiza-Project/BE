package com.gnagnoohc.scms.domain.program.dto.response;

/**
 * 비교과프로그램 만족도 설문 "완료 처리" 응답 DTO. 설문 문항/응답 내용을 저장하는 기능은 별도이며(이번 범위 제외),
 * 이 API는 ProgramApplication.surveyCompleted 플래그만 true로 갱신한다.
 */
public record ProgramApplicationSurveyResponseDTO(
        Integer applicationId,
        boolean surveyCompleted
) {
}
