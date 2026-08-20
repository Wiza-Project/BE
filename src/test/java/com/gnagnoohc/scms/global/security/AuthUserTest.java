package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WP-64: AuthUser 권한이 user_type 대분류뿐 아니라 user_role(N:M)도 반영하는지 검증합니다.
 * (버그였던 상태: user_role 을 몇 개를 등록해도 getAuthorities() 에 전혀 반영되지 않았음)
 */
class AuthUserTest {

    /** AppUser 는 세터가 없는 엔티티(@NoArgsConstructor protected)라 리플렉션으로 값을 채웁니다. */
    private AppUser appUser(String userType) {
        try {
            Constructor<AppUser> ctor = AppUser.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AppUser user = ctor.newInstance();
            ReflectionTestUtils.setField(user, "userId", 1);
            ReflectionTestUtils.setField(user, "universityNo", "2021000001");
            ReflectionTestUtils.setField(user, "userType", userType);
            ReflectionTestUtils.setField(user, "passwordHash", "hash");
            ReflectionTestUtils.setField(user, "accountStatus", "ACTIVE");
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void getAuthorities_withRoleCodes_includesUserTypeAndEachRole() {
        AppUser user = appUser("STAFF");
        AuthUser authUser = new AuthUser(user, List.of("COUNSELOR"));

        Set<String> authorities = authUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_COUNSELOR");
    }

    @Test
    void getAuthorities_withoutRoleCodes_returnsOnlyUserTypeRole() {
        AppUser user = appUser("STUDENT");
        AuthUser authUser = new AuthUser(user, List.of());

        Set<String> authorities = authUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(authorities).containsExactly("ROLE_STUDENT");
    }

    @Test
    void singleArgConstructor_neverAssignsUserRoleAuthorities() {
        // JwtTokenProvider 는 이 생성자로 만든 인스턴스의 getAuthorities()를 호출하지 않지만,
        // 혹시라도 principal 로 잘못 쓰이면 user_role 이 비어버린다는 걸 회귀 테스트로 못박아 둡니다.
        AppUser user = appUser("STAFF");
        AuthUser authUser = new AuthUser(user);

        assertThat(authUser.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STAFF");
    }
}
