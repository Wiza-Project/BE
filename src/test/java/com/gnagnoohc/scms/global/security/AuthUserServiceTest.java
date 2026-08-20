package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserRole;
import com.gnagnoohc.scms.domain.user.entity.UserRoleId;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * WP-64: 로그인/토큰 재발급 시 user_role 을 함께 조회해서 AuthUser 권한에 반영하는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AuthUserServiceTest {

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    UserRoleRepository userRoleRepository;

    @InjectMocks
    AuthUserService authUserService;

    @Test
    void loadUserById_combinesUserTypeAndUserRoleAuthorities() {
        AppUser user = appUser(1, "STAFF");
        when(appUserRepository.findById(1)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUser_UserId(1))
                .thenReturn(List.of(userRole(1, "COUNSELOR")));

        UserDetails authUser = authUserService.loadUserById(1);

        Set<String> authorities = authUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertThat(authorities).containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_COUNSELOR");
    }

    @Test
    void loadUserById_withNoUserRoleRows_keepsUserTypeAuthorityOnly() {
        AppUser user = appUser(2, "STUDENT");
        when(appUserRepository.findById(2)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUser_UserId(2)).thenReturn(List.of());

        UserDetails authUser = authUserService.loadUserById(2);

        assertThat(authUser.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void loadUserById_whenUserNotFound_throwsUsernameNotFoundException() {
        when(appUserRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authUserService.loadUserById(999))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_combinesUserTypeAndUserRoleAuthorities() {
        AppUser user = appUser(3, "STAFF");
        when(appUserRepository.findByUniversityNo("2021000001")).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUser_UserId(3))
                .thenReturn(List.of(userRole(3, "COUNSELOR")));

        UserDetails authUser = authUserService.loadUserByUsername("2021000001");

        assertThat(authUser.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_COUNSELOR");
    }

    /** AppUser 는 세터가 없는 엔티티(@NoArgsConstructor protected)라 리플렉션으로 값을 채웁니다. */
    private AppUser appUser(Integer userId, String userType) {
        AppUser user = newInstance(AppUser.class);
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "universityNo", "2021000001");
        ReflectionTestUtils.setField(user, "userType", userType);
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "accountStatus", "ACTIVE");
        return user;
    }

    /** UserRole/UserRoleId 도 세터가 없어 같은 방식으로 채웁니다. */
    private UserRole userRole(Integer userId, String roleCode) {
        UserRoleId id = newInstance(UserRoleId.class);
        ReflectionTestUtils.setField(id, "userId", userId);
        ReflectionTestUtils.setField(id, "roleCode", roleCode);

        UserRole userRole = newInstance(UserRole.class);
        ReflectionTestUtils.setField(userRole, "id", id);
        return userRole;
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
