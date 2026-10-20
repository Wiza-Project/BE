package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
public class AuthUser implements UserDetails {
    private final Integer id;
    private final String universityNo;
    private final String password;
    private final String userType;
    private final Integer departmentCodeId;
    private final String accountStatus;
    private final List<String> roleCodes;

    /**
     * user_role 을 채우지 않은 principal이 필요합니다. JwtTokenProvider 가 만드는 access/refresh
     * 토큰은 id/universityNo/userType/departmentCodeId claim만 사용하고 getAuthorities()는
     * 절대 호출하지 않으므로(AuthService.issueTokens 참고), 이 생성자로 만든 인스턴스를
     * SecurityContext principal 로는 쓰지 마세요 — 그 경우 user_role 기반 권한이 비어버립니다.
     */
    public AuthUser(AppUser user) {
        this(user, List.of());
    }

    /** 실제 인증 principal(SecurityContext)에는 이 생성자를 사용합니다 — AuthUserService 참고. */
    public AuthUser(AppUser user, List<String> roleCodes) {
        this.id = user.getUserId();
        this.universityNo = user.getUniversityNo();
        this.password = user.getPasswordHash();
        this.userType = user.getUserType();
        this.departmentCodeId = user.getDepartmentCode() == null
                ? null : user.getDepartmentCode().getCodeId();
        this.accountStatus = user.getAccountStatus();
        this.roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    /**
     * user_type(대분류) 권한에 user_role(N:M, 예: COUNSELOR) 권한을 더해서 반환합니다.
     * role_code 는 자유 문자열이라 별도 화이트리스트 없이 "ROLE_" 접두어만 붙입니다.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + userType));
        for (String roleCode : roleCodes) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        }
        return authorities;
    }

    @Override
    public String getUsername() {
        return universityNo;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(accountStatus);
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !"LOCKED".equals(accountStatus); }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
