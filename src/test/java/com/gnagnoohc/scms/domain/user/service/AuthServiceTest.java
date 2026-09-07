package com.gnagnoohc.scms.domain.user.service;

import com.gnagnoohc.scms.domain.user.dto.LoginFailureResponse;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.UserRoleRepository;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.common.service.AuditLogService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WP-106: "비밀번호가 틀린 건지 계정이 잠긴 건지 구분이 안 된다"는 QA 이슈 반영 검증.
 * 이미 확정된 계정 상태(LOCKED/DORMANT/WITHDRAWN)는 비밀번호를 맞히든 틀리든 그 사유
 * 그대로 노출되어야 하고, 6개월 미접속 휴면 "전환"만큼은 여전히 비밀번호 검증을
 * 통과해야만 일어나야 합니다 (DoS 방지 — AuthService 상단 주석 참고).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    UserRoleRepository userRoleRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    DormantAccountLocker dormantAccountLocker;

    @Mock
    LoginFailureTracker loginFailureTracker;

    @Mock
    AuditLogService auditLogService;

    @Mock
    ConsentVerifier consentVerifier;

    @InjectMocks
    AuthService authService;

    @Test
    void login_lockedAccount_withWrongPassword_throwsAccountLocked_notPasswordMismatch() {
        AppUser user = appUser(1, "LOCKED", Instant.now());
        when(appUserRepository.findByUniversityNo("2021000001")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("2021000001", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        // 이미 잠긴 계정이면 비밀번호 검증 자체를 하지 않습니다.
        verify(passwordEncoder, never()).matches(any(), any());
        verify(loginFailureTracker, never()).registerFailure(anyInt(), anyInt(), any(Boolean.class));
    }

    @Test
    void login_lockedAccount_withCorrectPassword_stillThrowsAccountLocked() {
        AppUser user = appUser(2, "LOCKED", Instant.now());
        when(appUserRepository.findByUniversityNo("2021000002")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("2021000002", "correct-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_withdrawnAccount_withWrongPassword_throwsAccountWithdrawn() {
        AppUser user = appUser(3, "WITHDRAWN", Instant.now());
        when(appUserRepository.findByUniversityNo("2021000003")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("2021000003", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
    }

    @Test
    void login_alreadyDormantAccount_withWrongPassword_throwsAccountDormant() {
        AppUser user = appUser(4, "DORMANT", Instant.now());
        when(appUserRepository.findByUniversityNo("2021000004")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("2021000004", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DORMANT);

        // 이미 DORMANT 로 저장돼 있던 경우이므로 새로 전환(lock)할 필요가 없습니다.
        verify(dormantAccountLocker, never()).lock(any());
    }

    @Test
    void login_activeAccount_withWrongPassword_throwsPasswordMismatch_andRegistersFailure() {
        AppUser user = appUser(5, "ACTIVE", Instant.now());
        ReflectionTestUtils.setField(user, "failedLoginCount", 0);
        when(appUserRepository.findByUniversityNo("2021000005")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("2021000005", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
                    // 최대 5회 중 1번째 실패이므로 잔여 4회, 아직 잠기지 않음.
                    assertThat(be.getData()).isEqualTo(new LoginFailureResponse(4, false));
                });

        verify(loginFailureTracker).registerFailure(5, 1, false);
    }

    @Test
    void login_activeAccount_withWrongPassword_onAttemptThatLocksAccount_reportsAccountLockedInData() {
        // 이번 시도가 5번째(마지막) 실패라 계정이 잠기지만, 응답 코드 자체는 여전히
        // PASSWORD_MISMATCH 입니다(AuthService 상단 "로그인 실패 잠금 정책" 주석 참고).
        // 대신 data.accountLocked=true 로 "방금 잠겼다"는 사실을 알립니다.
        AppUser user = appUser(9, "ACTIVE", Instant.now());
        ReflectionTestUtils.setField(user, "failedLoginCount", 4);
        when(appUserRepository.findByUniversityNo("2021000009")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("2021000009", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
                    assertThat(be.getData()).isEqualTo(new LoginFailureResponse(0, true));
                });

        verify(loginFailureTracker).registerFailure(9, 5, true);
    }

    @Test
    void login_activeAccount_withWrongPassword_andStaleLastLogin_doesNotTriggerDormantConversion() {
        // 6개월 훌쩍 지난 계정이라도, 비밀번호를 모르는 시도만으로는 휴면 전환이 일어나면 안 됩니다.
        Instant staleLastLogin = Instant.now().minus(400, ChronoUnit.DAYS);
        AppUser user = appUser(6, "ACTIVE", staleLastLogin);
        ReflectionTestUtils.setField(user, "failedLoginCount", 0);
        when(appUserRepository.findByUniversityNo("2021000006")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("2021000006", "wrong-password"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        verify(dormantAccountLocker, never()).lock(any());
    }

    @Test
    void login_activeAccount_withCorrectPassword_andStaleLastLogin_convertsToDormant() {
        Instant staleLastLogin = Instant.now().minus(400, ChronoUnit.DAYS);
        AppUser user = appUser(7, "ACTIVE", staleLastLogin);
        when(appUserRepository.findByUniversityNo("2021000007")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("2021000007", "correct-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DORMANT);

        verify(dormantAccountLocker).lock(7);
        verify(appUserRepository, never()).recordSuccessfulLogin(any(), any());
    }

    @Test
    void login_activeAccount_withCorrectPassword_andRecentActivity_succeeds() {
        AppUser user = appUser(8, "ACTIVE", Instant.now());
        when(appUserRepository.findByUniversityNo("2021000008")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(userRoleRepository.findByUser_UserId(8)).thenReturn(List.of());
        when(consentVerifier.hasAgreedAllRequired(eq(8), eq(ConsentModuleCode.COMMON), any(Instant.class)))
                .thenReturn(true);

        AuthResult result = authService.login("2021000008", "correct-password");

        assertThat(result).isNotNull();
        assertThat(result.body().user().commonConsentCompleted()).isTrue();
        verify(appUserRepository).recordSuccessfulLogin(eq(8), any());
        verify(dormantAccountLocker, never()).lock(any());
    }

    /** AppUser 는 세터가 없는 엔티티(@NoArgsConstructor protected)라 리플렉션으로 값을 채웁니다. */
    private AppUser appUser(Integer userId, String accountStatus, Instant lastLoginAt) {
        AppUser user = newInstance(AppUser.class);
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "universityNo", "202100000" + userId);
        ReflectionTestUtils.setField(user, "userType", "STUDENT");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "accountStatus", accountStatus);
        ReflectionTestUtils.setField(user, "lastLoginAt", lastLoginAt);
        ReflectionTestUtils.setField(user, "createdAt", lastLoginAt);
        return user;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
