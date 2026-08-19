package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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

    public AuthUser(AppUser user) {
        this.id = user.getUserId();
        this.universityNo = user.getUniversityNo();
        this.password = user.getPasswordHash();
        this.userType = user.getUserType();
        this.departmentCodeId = user.getDepartmentCode() == null
                ? null : user.getDepartmentCode().getCodeId();
        this.accountStatus = user.getAccountStatus();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userType));
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
