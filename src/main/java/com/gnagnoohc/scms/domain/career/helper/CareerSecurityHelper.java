package com.gnagnoohc.scms.domain.career.helper;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 취·창업(Career) 도메인 전용 교직원 권한 및 부서 인가 검증 헬퍼 컴포넌트
 *
 * <p><strong>[도메인 인가 규칙 및 설계 배경]</strong></p>
 * <ul>
 *   <li><b>허가 대상:</b> 취창업지원과({@code D400}) 소속 교직원 또는 시스템 총괄 관리자({@code ADMIN})</li>
 *   <li><b>컨트롤러 인가 (1차 방어):</b> Spring Security SpEL 식({@code @PreAuthorize("@careerSecurity.isCareerStaff(principal)")})을 통해 엔드포인트 진입 시점에 {@code boolean} 판별</li>
 *   <li><b>서비스 비즈니스 인가 (2차 방어):</b> 비즈니스 로직 내부에서 심사자/담당자 검증 및 {@link AppUser} 엔티티 영속 객체 확보</li>
 *   <li><b>공통 판별 일원화:</b> 핵심 인가 조건(D400 부서 일치 또는 ADMIN 여부)을 내부 {@code isCareerStaffOrAdmin()} 메서드로 일원화하여 정책 변경 시 단일 지점 관리 보장</li>
 * </ul>
 *
 * @author YUN
 */
@Component("careerSecurity")
@RequiredArgsConstructor
public class CareerSecurityHelper {

    public static final String CAREER_EMPLOYMENT_DEPT = "D400";
    private final AppUserRepository appUserRepository;

    /**
     * [컨트롤러 SpEL 인가 전용] 현재 인증된 사용자의 취창업지원과 교직원 또는 총괄 관리자 여부 판별
     *
     * <p>Spring Security의 {@code @PreAuthorize} 어노테이션 내에서 SpEL 식을 통해 호출<br>
     * 미인증 상태이거나 식별자가 없는 경우 즉시 {@code false}를 반환하며, 조건을 충족하지 못하면 Spring Security 필터에 의해 403 Forbidden 에러 발생</p>
     *
     * <pre>{@code
     * // 컨트롤러 적용 예시:
     * @PreAuthorize("@careerSecurity.isCareerStaff(principal)")
     * @GetMapping("/admin/career/postings")
     * public ApiResponse<...> getList(...) { ... }
     * }</pre>
     *
     * @param principal Spring Security 컨텍스트의 Principal 객체 (일반적으로 {@link AuthUser})
     * @return 취창업지원과(D400) 소속 교직원이거나 ADMIN인 경우 {@code true}, 그 외 {@code false}
     */
    public boolean isCareerStaff(Object principal) {
        if (!(principal instanceof AuthUser authUser) || authUser.getId() == null) {
            return false;
        }

        return appUserRepository.findById(authUser.getId())
                .map(this::isCareerStaffOrAdmin)
                .orElse(false);
    }

    /**
     * [서비스 비즈니스 로직 전용] 취창업지원과 교직원/관리자 권한 검증 및 사용자 엔티티 반환
     *
     * <p>서비스 계층 내부에서 권한을 재검증(2차 방어)하고, 승인/반려 심사자 정보 주입 등을 위해 검증 완료된 {@link AppUser} 엔티티를 반환<br>
     * 권한이 유효하지 않을 경우 {@link BusinessException} 예외를 즉시 발생시켜 트랜잭션을 차단</p>
     *
     * @param userId 검증할 사용자 식별자 PK ({@code app_user.user_id})
     * @return 인가 검증이 완료된 영속 {@link AppUser} 엔티티
     * @throws BusinessException 미로그인({@link ErrorCode#UNAUTHORIZED}),
     *                           사용자 미존재({@link ErrorCode#USER_NOT_FOUND}),
     *                           취창업지원과/관리자 권한 부족({@link ErrorCode#DEPARTMENT_FORBIDDEN})
     */
    public AppUser validateAndGetCareerStaff(Integer userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!isCareerStaffOrAdmin(user)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }

        return user;
    }

    /**
     * [내부 핵심 판별 로직] 사용자 엔티티의 취창업지원과(D400) 소속 여부 또는 총괄 관리자(ADMIN) 여부 판별
     *
     * <p>도메인 인가 규칙의 단일 진실 공급원(Single Source of Truth) 역할을 수행하며,
     * {@link #isCareerStaff(Object)}와 {@link #validateAndGetCareerStaff(Integer)}에서 공통 호출하는 판별용 로직</p>
     *
     * <ul>
     *   <li>부서 코드 일치: {@code user.departmentCode.code == "D400"}</li>
     *   <li>총괄 관리자: {@code user.userType == "ADMIN"} (대소문자 무시)</li>
     * </ul>
     *
     * @param user 검증 대상 {@link AppUser} 엔티티
     * @return 둘 중 하나의 인가 조건을 충족하면 {@code true}, 모두 만족하지 못하면 {@code false}
     */
    private boolean isCareerStaffOrAdmin(AppUser user) {
        boolean isCareerDept = user.getDepartmentCode() != null
                && CAREER_EMPLOYMENT_DEPT.equals(user.getDepartmentCode().getCode());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getUserType());
        return isCareerDept || isAdmin;
    }
}