package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingDetailResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingReviewRequestDTO;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.repository.CompanyAccountRepository;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobPostingServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @InjectMocks
    private JobPostingService jobPostingService;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private CompanyAccountRepository companyAccountRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private AppUser staffUser;
    private CompanyAccount company;
    private JobPosting pendingPosting;
    private JobPosting publishedPosting;

    @BeforeEach
    void setUp() {
        staffUser = BeanUtils.instantiateClass(AppUser.class);
        ReflectionTestUtils.setField(staffUser, "userId", 1);
        ReflectionTestUtils.setField(staffUser, "userName", "담당교직원");

        company = BeanUtils.instantiateClass(CompanyAccount.class);
        ReflectionTestUtils.setField(company, "companyAccountId", 10);
        ReflectionTestUtils.setField(company, "companyName", "우수 협약기업");

        pendingPosting = BeanUtils.instantiateClass(JobPosting.class);
        ReflectionTestUtils.setField(pendingPosting, "jobPostingId", 100);
        ReflectionTestUtils.setField(pendingPosting, "postingTitle", "백엔드 신입 개발자 채용");
        ReflectionTestUtils.setField(pendingPosting, "postingStatus", "PENDING");
        ReflectionTestUtils.setField(pendingPosting, "reviewStatus", "PENDING");
        ReflectionTestUtils.setField(pendingPosting, "postingType", "GENERAL");
        ReflectionTestUtils.setField(pendingPosting, "employmentType", "REGULAR");
        ReflectionTestUtils.setField(pendingPosting, "companyAccount", company);
        ReflectionTestUtils.setField(pendingPosting, "applicationStartsAt", Instant.now());
        ReflectionTestUtils.setField(pendingPosting, "applicationEndsAt", Instant.now().plus(14, ChronoUnit.DAYS));

        publishedPosting = BeanUtils.instantiateClass(JobPosting.class);
        ReflectionTestUtils.setField(publishedPosting, "jobPostingId", 200);
        ReflectionTestUtils.setField(publishedPosting, "postingTitle", "프론트엔드 인턴 채용");
        ReflectionTestUtils.setField(publishedPosting, "postingStatus", "PUBLISHED");
        ReflectionTestUtils.setField(publishedPosting, "reviewStatus", "APPROVED");
        ReflectionTestUtils.setField(publishedPosting, "postingType", "GENERAL");
        ReflectionTestUtils.setField(publishedPosting, "employmentType", "REGULAR");
        ReflectionTestUtils.setField(publishedPosting, "companyAccount", company);
        ReflectionTestUtils.setField(publishedPosting, "applicationStartsAt", Instant.now().minus(1, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(publishedPosting, "applicationEndsAt", Instant.now().plus(7, ChronoUnit.DAYS));
    }

    @Nested
    @DisplayName("[교직원 분기] 채용공고 관리 로직 검증")
    class StaffJobPostingTest {

        @Test
        @DisplayName("성공: 교직원이 공고를 직접 등록하면 정상 생성된다.")
        void createJobPosting_Success() {
            // given
            JobPostingCreateRequestDTO requestDTO = BeanUtils.instantiateClass(JobPostingCreateRequestDTO.class);
            ReflectionTestUtils.setField(requestDTO, "companyAccountId", 10);
            ReflectionTestUtils.setField(requestDTO, "postingTitle", "AI 연구원 채용");
            ReflectionTestUtils.setField(requestDTO, "applicationStartsAt", OffsetDateTime.now(KST));
            ReflectionTestUtils.setField(requestDTO, "applicationEndsAt", OffsetDateTime.now(KST).plusDays(30));

            given(companyAccountRepository.findById(10)).willReturn(Optional.of(company));
            given(jobPostingRepository.save(any(JobPosting.class))).willAnswer(invocation -> {
                JobPosting saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "jobPostingId", 300);
                return saved;
            });

            // when
            Integer createdPostingId = jobPostingService.createJobPosting(requestDTO);

            // then
            assertThat(createdPostingId).isEqualTo(300);
            verify(jobPostingRepository).save(any(JobPosting.class));
        }

        @Test
        @DisplayName("성공: 교직원이 대기(PENDING) 공고를 승인(APPROVED)하면 postingStatus가 PUBLISHED로 전이된다.")
        void reviewJobPosting_Approve_Success() {
            // given
            JobPostingReviewRequestDTO reviewDTO = BeanUtils.instantiateClass(JobPostingReviewRequestDTO.class);
            ReflectionTestUtils.setField(reviewDTO, "reviewStatus", "APPROVED");

            given(jobPostingRepository.findById(100)).willReturn(Optional.of(pendingPosting));

            // when
            jobPostingService.reviewJobPosting(100, staffUser.getUserId(), reviewDTO);

            // then
            assertThat(pendingPosting.getReviewStatus()).isEqualTo("APPROVED");
            assertThat(pendingPosting.getPostingStatus()).isEqualTo("PUBLISHED");
            assertThat(pendingPosting.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("성공: 교직원이 대기(PENDING) 공고를 반려(REJECTED)하면 reviewStatus는 REJECTED, postingStatus는 DRAFT로 전이된다.")
        void reviewJobPosting_Reject_Success() {
            // given
            JobPostingReviewRequestDTO reviewDTO = BeanUtils.instantiateClass(JobPostingReviewRequestDTO.class);
            ReflectionTestUtils.setField(reviewDTO, "reviewStatus", "REJECTED");
            ReflectionTestUtils.setField(reviewDTO, "rejectionReason", "급여 정보 누락으로 반려");

            given(jobPostingRepository.findById(100)).willReturn(Optional.of(pendingPosting));

            // when
            jobPostingService.reviewJobPosting(100, staffUser.getUserId(), reviewDTO);

            // then
            assertThat(pendingPosting.getReviewStatus()).isEqualTo("REJECTED");
            assertThat(pendingPosting.getPostingStatus()).isEqualTo("DRAFT");
        }
    }

    @Nested
    @DisplayName("[학생/공통 분기] 공고 상세 조회 검증")
    class StudentJobPostingTest {

        @Test
        @DisplayName("성공: 공고 상세 조회 시 DTO가 정상 변환되어 반환된다.")
        void getJobPostingDetail_Success() {
            // given
            given(jobPostingRepository.findByIdWithDetails(200)).willReturn(Optional.of(publishedPosting));

            // when
            JobPostingDetailResponseDTO response = jobPostingService.getJobPostingDetail(200);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getJobPostingId()).isEqualTo(200);
            assertThat(response.getPostingTitle()).isEqualTo("프론트엔드 인턴 채용");
            assertThat(response.getPostingStatus()).isEqualTo("PUBLISHED");
        }
    }
}