package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * 상담 일정 유스케이스에 필요한 사용자 잠금과 상담사 역할 확인만 제공한다.
 * 기본 신분인 user_type이 아니라 겸임 업무 역할인 user_role을 상담사 권한의 근거로 사용한다.
 */
public interface CounselUserRepository extends Repository<AppUser, Integer> {

    /**
     * 같은 상담사의 일정 등록·수정을 한 번에 하나씩 처리하기 위해 사용자 행을 잠근다.
     * 아직 일정 행이 없는 빈 구간도 이 공통 행을 기준으로 직렬화해야 동시 등록을 막을 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.userId = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") Integer userId);

    /**
     * 활성 계정 여부와 별도로 COUNSELOR 업무 역할이 실제 부여됐는지 확인한다.
     */
    @Query("""
            select case when count(role) > 0 then true else false end
            from UserRole role
            where role.id.userId = :userId
              and role.id.roleCode = 'COUNSELOR'
            """)
    boolean hasCounselorRole(@Param("userId") Integer userId);
}
