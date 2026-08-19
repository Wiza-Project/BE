package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthUserService implements UserDetailsService {
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String universityNo) throws UsernameNotFoundException {
        return appUserRepository.findByUniversityNo(universityNo)
                .map(AuthUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + universityNo));
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Integer userId) {
        return appUserRepository.findById(userId)
                .map(AuthUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId));
    }
}
