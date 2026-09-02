package com.gnagnoohc.scms.domain.counsel.dto.response;

/**
 * 상담사의 학번 정확 일치 조회 결과다. 학과·학년·연락처·계정 상태 등은 포함하지 않고
 * 대행 예약 화면이 학생을 특정하는 데 필요한 최소 식별 정보 세 가지만 반환한다.
 */
public record CounselorStudentLookupResponse(
        Integer studentId,
        String universityNo,
        String studentName
) {
}
