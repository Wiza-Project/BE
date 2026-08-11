package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.domain.user.entity.User;
import com.gnagnoohc.scms.global.common.enums.Department;
import com.gnagnoohc.scms.global.common.enums.UserType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 인증된 사용자 정보. 컨트롤러에서 아래처럼 꺼내 쓰세요.
 *
 *   @GetMapping("/my")
 *   public ApiResponse<?> my(@AuthenticationPrincipal AuthUser authUser) {
 *       Long userId = authUser.getId();
 *   }
 */
@Getter
public class AuthUser implements UserDetails {

    private final Long id;
    private final String loginId;
    private final String password;
    private final UserType userType;
    private final Department department;
    private final boolean active;

    public AuthUser(User user) {
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.password = user.getPassword();
        this.userType = user.getUserType();
        this.department = user.getDepartment();
        this.active = user.isActive();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(userType.toAuthority()));
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
