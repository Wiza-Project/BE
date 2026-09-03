package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 상담 관리 업무 범위를 활성 STAFF의 실제 역할 조합(user_role)만으로 판정하는 단일 지점이다.
 * 각 서비스가 ST200·ST300 조건을 각자 복제하면 어느 생명주기에서는 검사를 빠뜨릴 수 있어,
 * 판정 결과(Scope)를 여기서 한 번만 계산하고 목록 필터·단건 검증은 이 결과를 재사용한다.
 *
 * <p>ST200+ST300을 "합집합"으로 계산하지 않는다. 확정 권한 행렬(설계 문서 2장)에 따라
 * ST300을 함께 가진 사용자는 일반 상담사보다 더 많은 유형이 아니라, 오히려 CS200(진로상담)
 * 하나로 범위가 "축소"된다. 이 축소를 각 서비스가 아니라 이 클래스 하나에서만 표현한다.</p>
 */
@Component
@RequiredArgsConstructor
public class CounselManagementAccessPolicy {

    // 진로상담 판정 기준. 바뀔 수 있는 표시명(type_name)이 아니라 불변 코드로만 판정하며,
    // 이 문자열은 프로젝트 전체에서 이 클래스 한 곳에만 둔다.
    private static final String CAREER_TYPE_CODE = "CS200";
    // 상담사가 개별 일정을 열 수 있는 신청 경로. CENTER 유형은 두 범위 모두에서 제외 대상이다.
    private static final String APPLICATION_ROUTE_DIRECT = "DIRECT";

    private final CounselUserRepository counselUserRepository;

    public enum Scope {
        // ST200만 보유: 활성 DIRECT 유형 전체를 관리한다.
        ALL_DIRECT,
        // ST200+ST300 보유: 활성 DIRECT 유형 중 CS200(진로상담)만 관리한다.
        CAREER_ONLY
    }

    /**
     * 사용자 행을 잠그지 않는 조회 경로(목록 조회 등)에서 counselorId만으로 범위를 판정한다.
     * 활성 STAFF + ST200이 아니면 기존 인가 예외(A004/403)로 종료한다.
     */
    public Scope requireScope(Integer counselorId) {
        if (!counselUserRepository.isActiveCounselor(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return scopeOf(counselorId);
    }

    /**
     * 이미 사용자 행을 잠근(findByIdForUpdate) 뒤 호출한다. 잠금으로 얻은 행 값을 그대로 써서
     * accountStatus/userType은 다시 조회하지 않고, 역할 조합(UserRole)만 추가로 조회해 확인한다.
     * 이 잠금은 app_user 행 하나만 잠그므로 "이 요청이 커밋된 최신 역할 부여·회수 이후에 시작됐다"는
     * 것만 보장한다. UserRole 행 자체는 잠그지 않으므로, 이 메서드가 역할 조회를 끝낸 바로 뒤에
     * 다른 트랜잭션이 역할을 추가·회수하고 먼저 커밋되는 경쟁까지 막지는 못한다(동시 역할 변경
     * 프로토콜은 이 설계의 범위 밖이며, 이미 커밋된 역할 변경 이후 시작한 요청이 그 값을 본다는
     * 것만 보장하면 충분하다는 전제다).
     */
    public Scope requireScope(AppUser lockedCounselor) {
        boolean active = "ACTIVE".equals(lockedCounselor.getAccountStatus());
        boolean staff = "STAFF".equals(lockedCounselor.getUserType());
        boolean counselorRole = counselUserRepository.hasCounselorRole(lockedCounselor.getUserId());
        if (!active || !staff || !counselorRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return scopeOf(lockedCounselor.getUserId());
    }

    /**
     * 주어진 범위가 해당 상담 유형(코드·신청 경로)을 다뤄도 되는지 판정한다.
     * CENTER 유형은 상담사가 개별 일정을 여는 대상이 아니므로 두 범위 모두에서 거부한다.
     */
    public boolean allows(Scope scope, String typeCode, String applicationRoute) {
        if (!APPLICATION_ROUTE_DIRECT.equals(applicationRoute)) {
            return false;
        }
        if (scope == Scope.CAREER_ONLY) {
            return CAREER_TYPE_CODE.equals(typeCode);
        }
        return true;
    }

    /**
     * 학생 일정 노출·직접 예약 검증 전용 boolean 판정이다. 상담사 쪽 권한 예외를 학생에게
     * 그대로 노출하면 다른 사람의 계정 상태를 추측하는 단서가 될 수 있으므로, 예외 대신
     * false만 돌려주고 호출부가 기존 SCHEDULE_NOT_AVAILABLE(S002)로 응답을 통일하게 한다.
     */
    public boolean isEligibleForType(Integer counselorId, String typeCode, String applicationRoute) {
        if (!counselUserRepository.isActiveCounselor(counselorId)) {
            return false;
        }
        return allows(scopeOf(counselorId), typeCode, applicationRoute);
    }

    private Scope scopeOf(Integer counselorId) {
        boolean hasProfessorRole = counselUserRepository.hasProfessorRole(counselorId);
        return hasProfessorRole ? Scope.CAREER_ONLY : Scope.ALL_DIRECT;
    }
}
