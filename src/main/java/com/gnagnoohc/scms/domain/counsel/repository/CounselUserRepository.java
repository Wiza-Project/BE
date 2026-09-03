package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorStudentLookupResponse;
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
     * 학생용 상담 기능은 활성 계정의 기본 사용자 유형이 STUDENT인지 서비스에서 다시 확인한다.
     */
    @Query("""
            select case when count(user) > 0 then true else false end
            from AppUser user
            where user.userId = :userId
              and user.accountStatus = 'ACTIVE'
              and user.userType = 'STUDENT'
            """)
    boolean isActiveStudent(@Param("userId") Integer userId);

    /**
     * 일정 목록 조회 시 URL 권한 외에도 계정 활성 상태와 상담사 역할을 다시 확인한다.
     * 조회에는 쓰기 잠금이 필요하지 않으므로 일정 등록·수정용 조회와 분리한다.
     * user_type이 STAFF인지도 함께 확인한다. ST200은 겸임 업무 역할일 뿐이라 STAFF가 아닌
     * 사용자에게도 이론적으로 부여될 수 있으므로, 상담 관리 진입 조건인 "활성 STAFF"를 이 쿼리
     * 하나로 확정해 서비스마다 STAFF 여부를 따로 검사하지 않게 한다.
     */
    @Query("""
            select case when count(user) > 0 then true else false end
            from AppUser user
            where user.userId = :userId
              and user.accountStatus = 'ACTIVE'
              and user.userType = 'STAFF'
              and exists (
                  select role.id.userId
                  from UserRole role
                  where role.id.userId = user.userId
                    and role.id.roleCode = 'ST200'
              )
            """)
    boolean isActiveCounselor(@Param("userId") Integer userId);

    /**
     * 같은 상담사의 일정 등록·수정을 한 번에 하나씩 처리하기 위해 사용자 행을 잠근다.
     * 아직 일정 행이 없는 빈 구간도 이 공통 행을 기준으로 직렬화해야 동시 등록을 막을 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.userId = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") Integer userId);

    /**
     * 활성 계정 여부와 별도로 ST200 상담사 업무 역할이 실제 부여됐는지 확인한다.
     */
    @Query("""
            select case when count(role) > 0 then true else false end
            from UserRole role
            where role.id.userId = :userId
              and role.id.roleCode = 'ST200'
            """)
    boolean hasCounselorRole(@Param("userId") Integer userId);

    /**
     * ST300(진로상담 교수 등)의 겸임 역할 부여 여부만 확인한다. isActiveCounselor/hasCounselorRole과
     * 같은 형태의 조회이며, ST300 단독으로는 상담 관리에 진입할 수 없으므로 활성·STAFF 조건은
     * 이 메서드가 아니라 ST200 조건을 확인하는 쪽(isActiveCounselor)에서 이미 걸러진다.
     */
    @Query("""
            select case when count(role) > 0 then true else false end
            from UserRole role
            where role.id.userId = :userId
              and role.id.roleCode = 'ST300'
            """)
    boolean hasProfessorRole(@Param("userId") Integer userId);

    /**
     * 학번 완전 일치 하나만 조회한다. 부분 검색·전체 목록은 대상이 아니며, 학생이 아니거나
     * 비활성 계정이면 애초에 결과가 없어 서비스가 이유를 구분하지 않고 U001로 처리할 수 있다.
     * 전체 AppUser를 읽어 메모리에서 거르지 않도록 WHERE 절에서 바로 제한하고 최소 필드만 담는다.
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.response.CounselorStudentLookupResponse(
                user.userId,
                user.universityNo,
                user.userName
            )
            from AppUser user
            where user.universityNo = :universityNo
              and user.userType = 'STUDENT'
              and user.accountStatus = 'ACTIVE'
            """)
    Optional<CounselorStudentLookupResponse> findActiveStudentByUniversityNo(
            @Param("universityNo") String universityNo
    );
}
