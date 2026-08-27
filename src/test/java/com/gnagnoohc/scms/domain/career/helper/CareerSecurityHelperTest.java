package com.gnagnoohc.scms.domain.career.helper;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.global.security.AuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("CareerSecurityHelper 단위 테스트")
class CareerSecurityHelperTest {

    @InjectMocks
    private CareerSecurityHelper careerSecurityHelper;

    @Mock
    private AppUserRepository appUserRepository;

    @Nested
    @DisplayName("[SpEL] isCareerStaff(principal) 검증")
    class IsCareerStaffTest {

        @Test
        @DisplayName("취창업지원과(D400) 교직원이면 true를 반환한다.")
        void returnTrueWhenD400Staff() {
            // given
            AuthUser authUser = mock(AuthUser.class);
            given(authUser.getId()).willReturn(1);

            AppUser staffUser = mock(AppUser.class);
            CommonCode department = mock(CommonCode.class);
            given(department.getCode()).willReturn("D400");
            given(staffUser.getDepartmentCode()).willReturn(department);
            given(staffUser.getUserType()).willReturn("STAFF");

            given(appUserRepository.findById(1)).willReturn(Optional.of(staffUser));

            // when
            boolean result = careerSecurityHelper.isCareerStaff(authUser);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("부서 코드가 없어도 총괄 관리자(ADMIN)면 true를 반환한다.")
        void returnTrueWhenAdmin() {
            // given
            AuthUser authUser = mock(AuthUser.class);
            given(authUser.getId()).willReturn(2);

            AppUser adminUser = mock(AppUser.class);
            given(adminUser.getDepartmentCode()).willReturn(null);
            given(adminUser.getUserType()).willReturn("ADMIN");

            given(appUserRepository.findById(2)).willReturn(Optional.of(adminUser));

            // when
            boolean result = careerSecurityHelper.isCareerStaff(authUser);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("타 부서(D100) 교직원이면 false를 반환한다.")
        void returnFalseWhenOtherDepartmentStaff() {
            // given
            AuthUser authUser = mock(AuthUser.class);
            given(authUser.getId()).willReturn(3);

            AppUser otherStaff = mock(AppUser.class);
            CommonCode department = mock(CommonCode.class);
            given(department.getCode()).willReturn("D100");
            given(otherStaff.getDepartmentCode()).willReturn(department);
            given(otherStaff.getUserType()).willReturn("STAFF");

            given(appUserRepository.findById(3)).willReturn(Optional.of(otherStaff));

            // when
            boolean result = careerSecurityHelper.isCareerStaff(authUser);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("학생(STUDENT) 계정이면 false를 반환한다.")
        void returnFalseWhenStudent() {
            // given
            AuthUser authUser = mock(AuthUser.class);
            given(authUser.getId()).willReturn(4);

            AppUser student = mock(AppUser.class);
            given(student.getDepartmentCode()).willReturn(null);
            given(student.getUserType()).willReturn("STUDENT");

            given(appUserRepository.findById(4)).willReturn(Optional.of(student));

            // when
            boolean result = careerSecurityHelper.isCareerStaff(authUser);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("미인증 주체(null, 일반 문자열 등)가 전달되면 false를 반환한다.")
        void returnFalseWhenAnonymousOrInvalidPrincipal() {
            assertThat(careerSecurityHelper.isCareerStaff(null)).isFalse();
            assertThat(careerSecurityHelper.isCareerStaff("anonymousUser")).isFalse();
        }
    }

    @Nested
    @DisplayName("[서비스] validateAndGetCareerStaff(userId) 검증")
    class ValidateAndGetCareerStaffTest {

        @Test
        @DisplayName("취창업지원과(D400) 교직원이면 유저 엔티티를 정상 반환한다.")
        void successWhenD400Staff() {
            // given
            AppUser staffUser = mock(AppUser.class);
            CommonCode department = mock(CommonCode.class);
            given(department.getCode()).willReturn("D400");
            given(staffUser.getDepartmentCode()).willReturn(department);
            given(staffUser.getUserType()).willReturn("STAFF");

            given(appUserRepository.findById(1)).willReturn(Optional.of(staffUser));

            // when
            AppUser result = careerSecurityHelper.validateAndGetCareerStaff(1);

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(staffUser);
        }

        @Test
        @DisplayName("userId가 null이면 UNAUTHORIZED 예외를 던진다.")
        void throwUnauthorizedWhenUserIdIsNull() {
            assertThatThrownBy(() -> careerSecurityHelper.validateAndGetCareerStaff(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("존재하지 않는 유저 PK이면 USER_NOT_FOUND 예외를 던진다.")
        void throwUserNotFoundWhenUserDoesNotExist() {
            given(appUserRepository.findById(999)).willReturn(Optional.empty());

            assertThatThrownBy(() -> careerSecurityHelper.validateAndGetCareerStaff(999))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("권한이 없는 유저(타부서/학생)이면 DEPARTMENT_FORBIDDEN 예외를 던진다.")
        void throwDepartmentForbiddenWhenNotCareerStaff() {
            // given
            AppUser otherStaff = mock(AppUser.class);
            CommonCode department = mock(CommonCode.class);
            given(department.getCode()).willReturn("D200");
            given(otherStaff.getDepartmentCode()).willReturn(department);
            given(otherStaff.getUserType()).willReturn("STAFF");

            given(appUserRepository.findById(5)).willReturn(Optional.of(otherStaff));

            // when & then
            assertThatThrownBy(() -> careerSecurityHelper.validateAndGetCareerStaff(5))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DEPARTMENT_FORBIDDEN);
        }
    }
}