package com.gnagnoohc.scms.domain.user.service;

import com.gnagnoohc.scms.domain.user.dto.LoginResponse;
import com.gnagnoohc.scms.domain.user.dto.UserSummaryResponse;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
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
 * 2) 학번·교번 앞뒤 공백: trim 없이 조회해서 공백 섞인 입력이 "아이디 없음"으로 처리되어
 *    사용자에게 "비밀번호 불일치"라는 오해를 주는 메시지가 나갔습니다 → trim 후 조회.
 *    (비밀번호는 trim하지 않습니다 — 공백이 비밀번호의 일부일 수 있어서입니다.)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long DORMANT_THRESHOLD_MONTHS = 6;

    /**
     * 존재하지 않는 아이디로 로그인 시도 시에도 이 해시에 대해 BCrypt 검증을 한 번 실행해서
     * "아이디가 있어서 비밀번호 검증까지 갔는지"를 응답 시간으로 알아낼 수 없게 만듭니다.
     * 어떤 실제 계정과도 무관한, 이 목적 전용 상수입니다.
     */
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("no-such-user-timing-guard");

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final DormantAccountLocker dormantAccountLocker;

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

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        rejectIfNotLoginable(user);

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
        // 휴면 판정(rejectIfNotLoginable)과 lastLoginAt 갱신은 하지 않습니다.
        return issueTokens(user);
    }

    /**
     * ACTIVE 가 아니면 로그인을 거부합니다. ACTIVE 인 경우에도 마지막 활동으로부터
     * {@value #DORMANT_THRESHOLD_MONTHS}개월이 지났다면 이번 시도에서 DORMANT 로 전환하고 거부합니다.
     */
    private void rejectIfNotLoginable(AppUser user) {
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
        // ACTIVE (혹은 값이 비정상적으로 비어있는 경우까지 포함) 인 경우만 아래 휴면 기준일 검사로 진행합니다.

        Instant lastActivity = user.getLastLoginAt() != null ? user.getLastLoginAt() : user.getCreatedAt();
        Instant dormantSince = Instant.now().atZone(ZoneId.systemDefault())
                .minusMonths(DORMANT_THRESHOLD_MONTHS)
                .toInstant();
        if (lastActivity != null && lastActivity.isBefore(dormantSince)) {
            dormantAccountLocker.lock(user.getUserId());
            throw new BusinessException(ErrorCode.ACCOUNT_DORMANT);
        }
    }

    private AuthResult issueTokens(AppUser user) {
        AuthUser authUser = new AuthUser(user);
        String accessToken = jwtTokenProvider.createAccessToken(authUser);
        String refreshToken = jwtTokenProvider.createRefreshToken(authUser);

        LoginResponse body = LoginResponse.of(
                accessToken,
                jwtTokenProvider.getAccessTokenValiditySeconds(),
                UserSummaryResponse.from(user)
        );
        return new AuthResult(body, refreshToken);
    }
}
