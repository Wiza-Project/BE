package com.gnagnoohc.scms.domain.career.jobPreference;

import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.repository.JobPreferenceRepository;
import com.gnagnoohc.scms.domain.career.service.JobPreferenceService;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPreferenceServiceTest {

    @InjectMocks
    private JobPreferenceService jobPreferenceService;

    @Mock
    private JobPreferenceRepository jobPreferenceRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private CommonCodeRepository commonCodeRepository;

    @Nested
    @DisplayName("취업 희망조건 조회 테스트")
    class GetPreferenceTest {

        @Test
        @DisplayName("등록된 희망조건이 있으면 정상 조회되어 DTO로 반환된다.")
        void getMyPreference_Success() {
            // given
            Integer userId = 1;
            AppUser student = mock(AppUser.class);
            given(student.getUserId()).willReturn(userId);
            given(student.getUniversityNo()).willReturn("20240001");
            given(student.getUserName()).willReturn("홍길동");

            JobPreference preference = JobPreference.builder()
                    .student(student)
                    .preferredEmploymentType("REGULAR")
                    .minimumSalary(new BigDecimal("35000000.00"))
                    .build();
            ReflectionTestUtils.setField(preference, "jobPreferenceId", 100);
            ReflectionTestUtils.setField(preference, "updatedAt", Instant.now());

            given(jobPreferenceRepository.findByStudent_UserId(userId)).willReturn(Optional.of(preference));

            // when
            JobPreferenceResponseDTO response = jobPreferenceService.getMyPreference(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getJobPreferenceId()).isEqualTo(100);
            assertThat(response.getStudentUserId()).isEqualTo(userId);
            assertThat(response.getUniversityNo()).isEqualTo("20240001");
            assertThat(response.getStudentName()).isEqualTo("홍길동");
            assertThat(response.getPreferredEmploymentType()).isEqualTo("REGULAR");
        }

        @Test
        @DisplayName("등록된 희망조건이 없으면 RESOURCE_NOT_FOUND 예외가 발생한다.")
        void getMyPreference_NotFound() {
            // given
            Integer userId = 1;
            given(jobPreferenceRepository.findByStudent_UserId(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> jobPreferenceService.getMyPreference(userId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("취업 희망조건 등록/수정(Upsert) 테스트")
    class UpsertPreferenceTest {

        @Test
        @DisplayName("기존 희망조건이 없으면 새로 생성하여 영속화(save)한다.")
        void upsertPreference_CreateNew() {
            // given
            Integer userId = 1;
            JobPreferenceRequestDTO requestDTO = new JobPreferenceRequestDTO();
            ReflectionTestUtils.setField(requestDTO, "preferredRegionCodeId", 201);
            ReflectionTestUtils.setField(requestDTO, "preferredEmploymentType", "REGULAR");
            ReflectionTestUtils.setField(requestDTO, "minimumSalary", new BigDecimal("40000000.00"));

            AppUser student = mock(AppUser.class);
            given(student.getUserId()).willReturn(userId);
            given(student.getUniversityNo()).willReturn("20240001");
            given(student.getUserName()).willReturn("홍길동");

            CommonCode regionCode = mock(CommonCode.class);
            given(regionCode.getCodeId()).willReturn(201);
            given(regionCode.getCodeName()).willReturn("서울특별시 강남구");

            JobPreference newPreference = JobPreference.builder()
                    .student(student)
                    .preferredRegionCode(regionCode)
                    .preferredEmploymentType("REGULAR")
                    .minimumSalary(new BigDecimal("40000000.00"))
                    .build();
            ReflectionTestUtils.setField(newPreference, "jobPreferenceId", 101);

            given(appUserRepository.findById(userId)).willReturn(Optional.of(student));
            given(commonCodeRepository.findById(201)).willReturn(Optional.of(regionCode));
            given(jobPreferenceRepository.findByStudent_UserId(userId)).willReturn(Optional.empty());
            given(jobPreferenceRepository.save(any(JobPreference.class))).willReturn(newPreference);

            // when
            JobPreferenceResponseDTO response = jobPreferenceService.upsertPreference(userId, requestDTO);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getPreferredRegionCodeId()).isEqualTo(201);
            assertThat(response.getPreferredRegionName()).isEqualTo("서울특별시 강남구");
            verify(jobPreferenceRepository, times(1)).save(any(JobPreference.class));
        }

        @Test
        @DisplayName("기존 희망조건이 존재하면 엔티티의 update 비즈니스 메서드를 호출해 변경한다.")
        void upsertPreference_UpdateExisting() {
            // given
            Integer userId = 1;
            JobPreferenceRequestDTO requestDTO = new JobPreferenceRequestDTO();
            ReflectionTestUtils.setField(requestDTO, "preferredEmploymentType", "CONTRACT");
            ReflectionTestUtils.setField(requestDTO, "minimumSalary", new BigDecimal("45000000.00"));

            AppUser student = mock(AppUser.class);
            given(student.getUserId()).willReturn(userId);
            given(student.getUniversityNo()).willReturn("20240001");
            given(student.getUserName()).willReturn("홍길동");

            JobPreference existingPreference = JobPreference.builder()
                    .student(student)
                    .preferredEmploymentType("REGULAR")
                    .minimumSalary(new BigDecimal("35000000.00"))
                    .build();
            ReflectionTestUtils.setField(existingPreference, "jobPreferenceId", 100);

            given(appUserRepository.findById(userId)).willReturn(Optional.of(student));
            given(jobPreferenceRepository.findByStudent_UserId(userId)).willReturn(Optional.of(existingPreference));

            // when
            JobPreferenceResponseDTO response = jobPreferenceService.upsertPreference(userId, requestDTO);

            // then
            assertThat(response).isNotNull();
            assertThat(existingPreference.getPreferredEmploymentType()).isEqualTo("CONTRACT");
            assertThat(existingPreference.getMinimumSalary()).isEqualTo(new BigDecimal("45000000.00"));
            verify(jobPreferenceRepository, never()).save(any(JobPreference.class));
        }
    }
}