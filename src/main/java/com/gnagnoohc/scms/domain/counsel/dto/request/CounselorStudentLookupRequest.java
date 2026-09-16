package com.gnagnoohc.scms.domain.counsel.dto.request;

/**
 * 상담사가 학번으로 학생을 정확히 조회할 때 쓰는 요청 본문. 학번이 URL 쿼리스트링에 남지 않도록
 * POST 요청 본문으로만 받는다. trim·빈값·30자 상한 검증은 서비스(CounselorReservationService
 * .lookupStudent)가 담당하므로 이 DTO는 값을 그대로 전달하는 통로일 뿐이다.
 */
public record CounselorStudentLookupRequest(
        String universityNo
) {
}
