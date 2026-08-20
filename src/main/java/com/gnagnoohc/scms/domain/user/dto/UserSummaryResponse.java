package com.gnagnoohc.scms.domain.user.dto;

import com.gnagnoohc.scms.domain.user.entity.AppUser;

/**
 * 로그인 / 재발급 응답에 실어 보내는 사용자 요약 정보.
 *
 * department 는 예전처럼 고정된 Java enum이 아니라 common_code 참조라서,
 * 코드값(department)과 화면 표시용 한글명(departmentName)을 함께 내려줍니다.
 * (부서 코드가 없는 사용자는 둘 다 null)
 */
public record UserSummaryResponse(
        Integer id,
        String loginId,
        String name,
        String userType,
        String email,
        String phone,
        String department,
        String departmentName
) {
    public static UserSummaryResponse from(AppUser user) {
        var departmentCode = user.getDepartmentCode();
        return new UserSummaryResponse(
                user.getUserId(),
                user.getUniversityNo(),
                user.getUserName(),
                user.getUserType(),
                user.getEmail(),
                user.getPhone(),
                departmentCode == null ? null : departmentCode.getCode(),
                departmentCode == null ? null : departmentCode.getCodeName()
        );
    }
}
