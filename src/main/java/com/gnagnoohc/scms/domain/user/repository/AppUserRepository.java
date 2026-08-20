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
     * 로그인 성공 시각 기록. 실패 카운트도 함께 0으로 초기화합니다(정상 로그인했으니 이전 실패는 무의미).
     * AppUser 엔티티에는 세터/비즈니스 메서드가 없어(엔티티 수정 금지) 벌크 업데이트로 반영합니다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.lastLoginAt = :loginAt, u.failedLoginCount = 0 WHERE u.userId = :userId")
    void recordSuccessfulLogin(@Param("userId") Integer userId, @Param("loginAt") Instant loginAt);

    /**
     * 휴면 전환. AuthService.DormantAccountLocker 가 별도 트랜잭션(REQUIRES_NEW)으로 호출해서,
     * 이 업데이트 직후 로그인 거부 예외를 던지더라도(원 트랜잭션은 롤백) 휴면 전환 자체는 커밋되도록 합니다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.accountStatus = 'DORMANT' WHERE u.userId = :userId")
    void markDormant(@Param("userId") Integer userId);

    /** 비밀번호 실패 횟수 갱신(잠금 임계치 미도달). LoginFailureTracker 참고. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.failedLoginCount = :count WHERE u.userId = :userId")
    void updateFailedLoginCount(@Param("userId") Integer userId, @Param("count") Integer count);

    /** 실패 횟수가 임계치에 도달해 계정을 잠글 때. LoginFailureTracker 참고. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.accountStatus = 'LOCKED', u.failedLoginCount = :count, u.lockedAt = :lockedAt WHERE u.userId = :userId")
    void lockAccount(@Param("userId") Integer userId, @Param("count") Integer count, @Param("lockedAt") Instant lockedAt);
}
