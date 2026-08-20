package com.gnagnoohc.scms.domain.user.repository;

import com.gnagnoohc.scms.domain.user.entity.UserRole;
import com.gnagnoohc.scms.domain.user.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    /**
     * 로그인/토큰 재발급 시 AuthUser 권한 계산에 사용합니다 (AuthUserService 참고).
     * role_code 는 common_code FK 가 아닌 자유 문자열(varchar(40))이라, 값 자체를 화이트리스트로
     * 검증하지 않고 "ROLE_" 접두어만 붙여 그대로 GrantedAuthority 로 씁니다.
     */
    List<UserRole> findByUser_UserId(Integer userId);
}
