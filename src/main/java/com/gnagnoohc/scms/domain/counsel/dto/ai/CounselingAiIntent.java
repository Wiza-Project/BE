package com.gnagnoohc.scms.domain.counsel.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM이 대화에서 추출하는 의도. 응답에 그대로 노출하지 않고 서버가 검증한 뒤에만 사용한다.
 *
 * <p>모델은 scheduleId를 직접 만들지 못한다. 직전 후보의 번호(1부터)만 고르게 해서 존재하지 않는 일정을 막는다.
 * action은 문자열로 받아 서버가 허용 값인지 확인한다(OPEN_GENERAL_APPLICATION은 서버만 결정).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CounselingAiIntent(
        String action,
        // 화면(FE)이 후보를 1번부터 보여 주므로 모델도 학생이 말한 번호 그대로(1부터) 돌려준다.
        // 0 기반 변환은 서버가 한다. 모델에게 N-1 계산을 맡기면 틀리기 쉽기 때문이다.
        Integer selectedCandidateNumber,
        Integer counselingTypeId,
        Integer recommendedCounselingTypeId,
        String requestContent,
        // 모델의 null·빈 값은 "이전 요약 유지"로 본다(모델은 새 정보가 없을 때 빈 값을 내기 쉽다).
        // 그래서 신청 내용 전체 삭제는 이 값으로만 받는다(clearFilters와 같은 이유).
        Boolean clearRequestContent,
        CounselingAiChatRequest.Filters filters,
        // 모델은 "조건 없음"을 null 대신 빈 객체로 보내기 쉬워서, 빈 filters만으로는 "유지"인지 "해제"인지 알 수 없다.
        // 그래서 조건 해제("아무 날이나 괜찮아")는 이 값으로만 받는다.
        Boolean clearFilters,
        String reply
) {
}
