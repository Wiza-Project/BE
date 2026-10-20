package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthUserService implements UserDetailsService {
    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String universityNo) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUniversityNo(universityNo)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + universityNo));
        return toAuthUser(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Integer userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId));
        return toAuthUser(user);
    }

    /**
     * user_role(N:M) 을 함께 조회해서 AuthUser 권한에 반영합니다.
     * JwtAuthenticationFilter 가 매 요청마다 loadUserById 를 다시 호출하므로,
     * 관리자가 user_role 을 변경하면 access 토큰 재발급 없이도 다음 요청부터 바로 반영됩니다.
     */
    private AuthUser toAuthUser(AppUser user) {
        List<String> roleCodes = userRoleRepository.findByUser_UserId(user.getUserId()).stream()
                .map(userRole -> userRole.getId().getRoleCode())
                .toList();
        return new AuthUser(user, roleCodes);
    }
}
