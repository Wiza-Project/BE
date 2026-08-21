package com.gnagnoohc.scms.domain.user.service;

import com.gnagnoohc.scms.domain.user.dto.LoginResponse;
import com.gnagnoohc.scms.domain.user.dto.UserSummaryResponse;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.UserRoleRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.global.security.AuthUser;
import com.gnagnoohc.scms.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * 로그인 / 토큰 재발급.
 *
 * ── 엔티티 수정 금지 제약에 대한 메모 ──────────────────────────────
 * AppUser 는 이미 만들어져 있는 엔티티(테이블정의서 app_user 그대로)라 손대지 않았습니다.
 * 세터/비즈니스 메서드가 전혀 없는 엔티티라, 계정 상태(accountStatus)·마지막 로그인
 * (lastLoginAt) 변경은 모두 AppUserRepository 의 @Modifying 벌크 업데이트로 처리합니다.
 *
 * ── 휴면 잠금 정책 ───────────────────────────────────────────────
 * "마지막 로그인(없으면 가입일)으로부터 6개월 이상 지난 뒤 로그인을 시도"하면
 * 그 시점에 계정을 DORMANT 로 전환하고 로그인을 거부합니다. 배치로 미리 잠그는 방식이
 * 아니라 "로그인 시도 시" 판정하는 방식임에 주의하세요 (요구사항 원문 그대로).
 * 휴면 전환이 실제로 커밋되도록 하는 이유는 DormantAccountLocker 주석 참고.
 * 휴면 해제(본인확인) 절차는 이 기능 범위 밖입니다.
 * 이 "동적 전환" 판정(rejectIfNewlyDormant)만큼은 비밀번호 검증에 성공한 뒤에만
 * 실행합니다 — 비밀번호를 모르는 사람이 아이디만 알고 반복 시도해서 남의 멀쩡한 계정을
 * 휴면으로 만들어 버리는 걸 막기 위해서입니다. (이미 DORMANT 로 저장돼 있는 계정은
 * 이 값과 무관하게 rejectIfAlreadyBlocked 에서 비밀번호 검증 전에 걸러집니다.)
 *
 * ── 로그인 실패 잠금 정책 ─────────────────────────────────────────
 * 비밀번호를 {@value #MAX_FAILED_LOGIN_ATTEMPTS}회 연속으로 틀리면 계정을 LOCKED 로
 * 전환합니다. 존재하는 아이디에만 적용됩니다(존재하지 않는 아이디는 애초에 카운트할
 * failedLoginCount 가 없음). 정상 로그인에 성공하면 카운트는 0으로 초기화됩니다
 * (recordSuccessfulLogin 참고).
 * 잠금을 "유발한 바로 그 시도"의 응답은 여전히 PASSWORD_MISMATCH 입니다 — 그 순간까지는
 * 계정이 아직 ACTIVE 였고, 상태 판정(rejectIfAlreadyBlocked)은 비밀번호 검증보다 먼저
 * 실행되기 때문입니다. 하지만 그 다음 시도부터는 비밀번호를 맞히든 틀리든 즉시
 * ACCOUNT_LOCKED 로 응답합니다.
 * (QA-106: "비밀번호가 틀린 건지 계정이 잠긴 건지 구분이 안 된다" 반영. 이전에는 올바른
 * 비밀번호를 넣어야만 잠김 사실이 드러났는데, "이미 잠긴 계정인지"는 비밀번호를 몰라도
 * 노출해도 되는 정보로 판단해 순서를 바꿨습니다 — 대신 존재하지 않는 아이디는 여전히
 * PASSWORD_MISMATCH만 반환하므로 "아이디가 아예 존재하는지" 자체는 노출되지 않습니다.)
 * 관리자에 의한 잠금 해제 절차는 이 기능 범위 밖입니다(휴면 해제와 동일).
 *
 * ── Refresh Token 보관 위치 결정 ──────────────────────────────────
 * domain/user/package-info.java 체크리스트의 미결정 사항이었습니다.
 * DB/Redis 저장 없이 stateless JWT로 발급하고, httpOnly 쿠키(AuthController)로만
 * 전달하기로 결정했습니다. CorsConfig 가 이미 allowCredentials(true) 로 되어 있어
 * 쿠키 인증을 전제로 설계되어 있었습니다.
 * 트레이드오프: 별도 저장소가 없으므로 특정 refresh token 하나만 콕 집어 폐기(강제
 * 로그아웃, 탈취 대응)할 수 없습니다. 그런 요구가 생기면 저장소 도입을 재검토하세요.
 *
 * ── QA 발견 사항 반영 ────────────────────────────────────────────
 * 1) 타이밍 사이드채널: 존재하지 않는 아이디는 BCrypt 검증 없이 바로 실패해서, 존재하는
 *    아이디(BCrypt 연산으로 수백ms 소요)와 응답 시간 차이가 커 아이디 존재 여부가 노출됐습니다.
 *    (실측: 존재 0.3~0.5초 vs 미존재 0.01초) → 아이디가 없어도 더미 해시로 BCrypt 검증을
 *    똑같이 태워서 시간을 맞춥니다.
 * 2) (WP-106 이전 이력) 학번·교번 앞뒤 공백: trim 없이 조회해서 공백 섞인 입력이 "아이디
 *    없음"으로 처리되어 사용자에게 "비밀번호 불일치"라는 오해를 주는 메시지가 나갔습니다.
 *    지금은 LoginRequest 의 @Pattern("\\d{8}") 이 공백 섞인 입력 자체를 "숫자 8자리로
 *    입력해주세요"로 먼저 걸러내므로(WP-106: 아이디에 공백을 허용하지 않기로 확정),
 *    HTTP 요청 경로에서는 이 메서드까지 공백이 섞인 값이 들어오지 않습니다. 아래 trim() 은
 *    DTO 검증을 거치지 않고 이 메서드를 직접 호출하는 경우(테스트 등)에 대한 방어용으로만
 *    남겨둡니다. (비밀번호는 trim하지 않습니다 — 공백이 비밀번호의 일부일 수 있어서입니다.)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long DORMANT_THRESHOLD_MONTHS = 6;
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    /**
     * 존재하지 않는 아이디로 로그인 시도 시에도 이 해시에 대해 BCrypt 검증을 한 번 실행해서
     * "아이디가 있어서 비밀번호 검증까지 갔는지"를 응답 시간으로 알아낼 수 없게 만듭니다.
     * 어떤 실제 계정과도 무관한, 이 목적 전용 상수입니다.
     */
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("no-such-user-timing-guard");

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final DormantAccountLocker dormantAccountLocker;
    private final LoginFailureTracker loginFailureTracker;

    @Transactional
    public AuthResult login(String universityNo, String rawPassword) {
        String normalizedUniversityNo = universityNo == null ? null : universityNo.trim();

        AppUser user = appUserRepository.findByUniversityNo(normalizedUniversityNo).orElse(null);
        if (user == null) {
            // 더미 해시로라도 BCrypt 연산을 태워서 "존재하는 아이디 + 틀린 비밀번호" 경로와
            // 소요 시간을 비슷하게 맞춥니다 (결과는 어차피 버립니다).
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 이미 확정되어 저장된 상태(LOCKED/DORMANT/WITHDRAWN)는 비밀번호 검증보다 먼저
        // 판정합니다 — 비밀번호를 맞히든 틀리든 상관없이 정확한 사유를 보여주기 위해서입니다.
        rejectIfAlreadyBlocked(user);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerLoginFailure(user);
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 6개월 미접속 휴면 전환은 상태 변경을 동반하므로 비밀번호 검증을 통과한 뒤에만 판정합니다.
        rejectIfNewlyDormant(user);

        appUserRepository.recordSuccessfulLogin(user.getUserId(), Instant.now());

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResult reissue(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Integer userId = jwtTokenProvider.parseUserId(refreshToken);
        AppUser user = userId == null ? null : appUserRepository.findById(userId).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getAccountStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 재발급은 "활동 중" 세션 연장이지 새 로그인 시도가 아니므로
        // 휴면 판정(rejectIfAlreadyBlocked/rejectIfNewlyDormant)과 lastLoginAt 갱신은 하지 않습니다.
        return issueTokens(user);
    }

    /**
     * 이미 저장돼 있는 상태(DORMANT/LOCKED/WITHDRAWN)면 로그인을 거부합니다. 비밀번호 검증
     * 이전에 호출되므로, 비밀번호를 몰라도 "이미 확정된" 계정 상태는 그대로 노출됩니다.
     * ACTIVE(혹은 값이 비정상적으로 비어있는 경우)는 여기서 통과시키고 이후 비밀번호 검증으로 넘어갑니다.
     */
    private void rejectIfAlreadyBlocked(AppUser user) {
        String status = user.getAccountStatus();
        if ("DORMANT".equals(status)) {
            throw new BusinessException(ErrorCode.ACCOUNT_DORMANT);
        }
        if ("LOCKED".equals(status)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if ("WITHDRAWN".equals(status)) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    /**
     * 비밀번호 검증을 통과한 ACTIVE 계정에 대해서만 호출합니다. 마지막 활동으로부터
     * {@value #DORMANT_THRESHOLD_MONTHS}개월이 지났다면 이번 시도에서 DORMANT 로 전환하고 거부합니다.
     */
    private void rejectIfNewlyDormant(AppUser user) {
        Instant lastActivity = user.getLastLoginAt() != null ? user.getLastLoginAt() : user.getCreatedAt();
        Instant dormantSince = Instant.now().atZone(ZoneId.systemDefault())
                .minusMonths(DORMANT_THRESHOLD_MONTHS)
                .toInstant();
        if (lastActivity != null && lastActivity.isBefore(dormantSince)) {
            dormantAccountLocker.lock(user.getUserId());
            throw new BusinessException(ErrorCode.ACCOUNT_DORMANT);
        }
    }

    /**
     * 실패 횟수를 늘리고, {@value #MAX_FAILED_LOGIN_ATTEMPTS}회에 도달하면 계정을 잠급니다.
     * DormantAccountLocker와 동일한 이유로 커밋을 보장하기 위해 반영 자체는
     * LoginFailureTracker(REQUIRES_NEW)에 위임합니다.
     */
    private void registerLoginFailure(AppUser user) {
        int newFailedCount = user.getFailedLoginCount() + 1;
        boolean shouldLock = newFailedCount >= MAX_FAILED_LOGIN_ATTEMPTS;
        loginFailureTracker.registerFailure(user.getUserId(), newFailedCount, shouldLock);
    }

    private AuthResult issueTokens(AppUser user) {
        AuthUser authUser = new AuthUser(user);
        String accessToken = jwtTokenProvider.createAccessToken(authUser);
        String refreshToken = jwtTokenProvider.createRefreshToken(authUser);

        // FE ProtectedRoute의 role_code 기반 분기(WP-82)를 위해 응답 바디에만 실어 보냅니다.
        // JWT claim에는 안 담습니다 — authUser 는 JwtTokenProvider 가 claim 생성에만 쓰고
        // getAuthorities()를 호출하지 않는 role-less 인스턴스라(AuthUser 1-인자 생성자 참고),
        // 여기서 SecurityContext principal 로 재사용하면 안 됩니다.
        List<String> roleCodes = userRoleRepository.findByUser_UserId(user.getUserId()).stream()
                .map(userRole -> userRole.getId().getRoleCode())
                .toList();

        LoginResponse body = LoginResponse.of(
                accessToken,
                jwtTokenProvider.getAccessTokenValiditySeconds(),
                UserSummaryResponse.from(user, roleCodes)
        );
        return new AuthResult(body, refreshToken);
    }
}
