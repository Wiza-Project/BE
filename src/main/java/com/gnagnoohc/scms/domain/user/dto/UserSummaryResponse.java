package com.gnagnoohc.scms.domain.user.dto;

import com.gnagnoohc.scms.domain.user.entity.AppUser;

import java.util.List;

/**
 * 로그인 / 재발급 응답에 실어 보내는 사용자 요약 정보.
 *
 * department 는 예전처럼 고정된 Java enum이 아니라 common_code 참조라서,
 * 코드값(department)과 화면 표시용 한글명(departmentName)을 함께 내려줍니다.
 * (부서 코드가 없는 사용자는 둘 다 null)
 *
 * roleCodes 는 user_role(N:M) 기반 겸임 역할 목록입니다(예: ["ST200"]). FE ProtectedRoute가
 * role_code 기반으로 권한 분기를 하려면 여기서 내려받는 것 외엔 방법이 없습니다 — JWT claim에는
 * 안 담고(AuthService.issueTokens 참고, 토큰은 id/universityNo/userType/departmentCodeId만
 * 가짐) 응답 바디로만 내려줍니다. 테이블 설계가 N:M이라 필드도 그대로 배열로 유지합니다
 * (현재 운영상 겸임을 안 하더라도, 그건 데이터가 그렇다는 것뿐 스키마/API 계약과는 별개).
 * 겸임 역할이 없으면 빈 배열([])이며 null은 없습니다.
 *
 * commonConsentCompleted 는 현재 유효한 COMMON 필수 약관 전부에 대한 미철회 동의 보유 여부입니다
 * — FE가 로그인 직후 동의 화면 진입을 별도 API 호출 없이 분기하는 용도입니다. 판정 입력이
 * DB(consent_policy·user_consent)뿐이라 브라우저·기기가 바뀌어도 값이 같습니다.
 */
public record UserSummaryResponse(
        Integer id,
        String loginId,
        String name,
        String userType,
        List<String> roleCodes,
        String email,
        String phone,
        String department,
        String departmentName,
        boolean commonConsentCompleted
) {
    public static UserSummaryResponse from(AppUser user, List<String> roleCodes, boolean commonConsentCompleted) {
        var departmentCode = user.getDepartmentCode();
        return new UserSummaryResponse(
                user.getUserId(),
                user.getUniversityNo(),
                user.getUserName(),
                user.getUserType(),
                roleCodes == null ? List.of() : roleCodes,
                user.getEmail(),
                user.getPhone(),
                departmentCode == null ? null : departmentCode.getCode(),
                departmentCode == null ? null : departmentCode.getCodeName(),
                commonConsentCompleted
        );
    }
}
