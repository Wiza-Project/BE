package com.gnagnoohc.scms.domain.user.dto;

/**
 * 로그인 실패(U003 PASSWORD_MISMATCH) 응답 바디의 data 에 실어 보내는 부가 정보.
 *
 * 서버가 이미 추적 중인 값(AuthService.registerLoginFailure 의
 * newFailedCount/shouldLock)을 그대로 실어 보내 FE가 로컬 카운터 없이도 정확한 안내를
 * 띄울 수 있게 합니다.
 *
 * accountLocked 는 "이 응답을 유발한 시도로 계정이 방금 잠겼는지"를 뜻합니다. 잠금을
 * 유발한 바로 그 시도의 에러 코드는 여전히 U003(PASSWORD_MISMATCH)입니다 — AuthService
 * 상단 "로그인 실패 잠금 정책" 주석 참고. 그다음 시도부터는 U005(ACCOUNT_LOCKED)로
 * 응답이 바뀝니다.
 */
public record LoginFailureResponse(
        int remainingAttempts,
        boolean accountLocked
) {
}
