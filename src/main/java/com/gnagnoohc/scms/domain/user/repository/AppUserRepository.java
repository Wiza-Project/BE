package com.gnagnoohc.scms.domain.user.repository;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Integer> {
    Optional<AppUser> findByUniversityNo(String universityNo);
    boolean existsByUniversityNo(String universityNo);

    /**
     * 로그인 성공 시각 기록.
     * AppUser 엔티티에는 세터/비즈니스 메서드가 없어(엔티티 수정 금지) 벌크 업데이트로 반영합니다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.lastLoginAt = :loginAt WHERE u.userId = :userId")
    void recordSuccessfulLogin(@Param("userId") Integer userId, @Param("loginAt") Instant loginAt);

    /**
     * 휴면 전환. AuthService.DormantAccountLocker 가 별도 트랜잭션(REQUIRES_NEW)으로 호출해서,
     * 이 업데이트 직후 로그인 거부 예외를 던지더라도(원 트랜잭션은 롤백) 휴면 전환 자체는 커밋되도록 합니다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.accountStatus = 'DORMANT' WHERE u.userId = :userId")
    void markDormant(@Param("userId") Integer userId);
}
