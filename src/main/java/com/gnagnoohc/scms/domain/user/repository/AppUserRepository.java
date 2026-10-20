package com.gnagnoohc.scms.domain.user.repository;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Integer> {

    /**
     * 같은 학생의 이력서 생성·수정·버전 생성을 직렬화한다.
     * 이력서가 아직 없는 경우에도 항상 존재하는 학생 행을 잠금 대상으로 삼는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.userId = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") Integer userId);

    //부서코드를 JOIN FETCH로 미리 로딩
    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.departmentCode WHERE u.universityNo = :universityNo")
    Optional<AppUser> findByUniversityNo(@Param("universityNo") String universityNo);

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
     * 이미 DORMANT면 조건에 걸려 영향받은 행이 0건이 되므로, 동시에 들어온 다른 요청이 먼저
     * 전환시킨 경우 뒤이은 호출은 중복으로 반영되지 않습니다(중복 감사 로그 방지용 — DormantAccountLocker 참고).
     * account_status='ACTIVE' 단정 대신 '<> DORMANT'로 걸어, rejectIfAlreadyBlocked 가 통과시키는
     * 비정상적으로 비어있는 값도 기존과 동일하게 전환 대상으로 남겨둡니다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.accountStatus = 'DORMANT' WHERE u.userId = :userId AND u.accountStatus <> 'DORMANT'")
    int markDormant(@Param("userId") Integer userId);

    /** 비밀번호 실패 횟수 갱신(잠금 임계치 미도달). LoginFailureTracker 참고. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.failedLoginCount = :count WHERE u.userId = :userId")
    void updateFailedLoginCount(@Param("userId") Integer userId, @Param("count") Integer count);

    /**
     * 실패 횟수가 임계치에 도달해 계정을 잠글 때. LoginFailureTracker 참고.
     * 이미 LOCKED면 조건에 걸려 영향받은 행이 0건이 되므로, 동시에 들어온 다른 요청이 먼저
     * 잠근 경우 뒤이은 호출은 중복으로 반영되지 않습니다(중복 감사 로그 방지용).
     * account_status='ACTIVE' 단정 대신 '<> LOCKED'로 걸어 markDormant 와 동일한 이유로
     * 비정상적으로 비어있는 값도 기존과 동일하게 잠금 대상으로 남겨둡니다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AppUser u SET u.accountStatus = 'LOCKED', u.failedLoginCount = :count, u.lockedAt = :lockedAt "
            + "WHERE u.userId = :userId AND u.accountStatus <> 'LOCKED'")
    int lockAccount(@Param("userId") Integer userId, @Param("count") Integer count, @Param("lockedAt") Instant lockedAt);
}
