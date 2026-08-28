package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.career.repository.JobPreferenceRepository;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 학생 맞춤형 채용공고 추천 및 매칭 서비스
 *
 * @author YUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobMatchingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobPreferenceRepository jobPreferenceRepository;
    private final ConsentVerifier consentVerifier;

    /**
     * [학생] 맞춤 추천 채용공고 목록 조회 (PROFILING 선택 동의 분기 + 희망직무 NCS 매칭)
     */
    public List<JobPostingSummaryResponseDTO> getRecommendedPostingsForStudent(Integer studentUserId) {
        Instant now = Instant.now();

        // 1. AI 맞춤 추천(PROFILING) 선택 동의 여부 확인
        boolean hasProfilingConsent = consentVerifier.hasValidConsent(
                studentUserId, ConsentModuleCode.CAREER, ConsentType.PROFILING, now);

        // 동의하지 않은 경우 -> 기본 최신 공개 공고 반환
        if (!hasProfilingConsent) {
            return jobPostingRepository.findDefaultActivePostingsWithDetails(now)
                    .stream()
                    .map(this::convertToSummaryDTO)
                    .toList();
        }

        // 2. 동의한 경우 -> 학생의 희망직무(NCS 공통코드 ID) 조회
        Integer preferredNcsCodeId = jobPreferenceRepository.findByStudent_UserId(studentUserId)
                .map(JobPreference::getNcsCode)
                .map(CommonCode::getCodeId)
                .orElse(null);

        List<JobPosting> result = List.of();
        if (preferredNcsCodeId != null) {
            result = jobPostingRepository.findRecommendedPostingsWithDetails(preferredNcsCodeId, now);
        }

        // 매칭 결과가 없거나 희망 직무 미설정인 경우 -> 기본 최신 공고 반환
        if (result.isEmpty()) {
            result = jobPostingRepository.findDefaultActivePostingsWithDetails(now);
        }

        return result.stream()
                .map(this::convertToSummaryDTO)
                .toList();
    }

    private JobPostingSummaryResponseDTO convertToSummaryDTO(JobPosting jp) {
        return JobPostingSummaryResponseDTO.builder()
                .jobPostingId(jp.getJobPostingId())
                .companyAccountId(jp.getCompanyAccount().getCompanyAccountId())
                .companyName(jp.getCompanyAccount().getCompanyName())
                .ncsCodeName(jp.getNcsCode() != null ? jp.getNcsCode().getCodeName() : null)
                .regionCodeName(jp.getRegionCode() != null ? jp.getRegionCode().getCodeName() : null)
                .postingTitle(jp.getPostingTitle())
                .employmentType(jp.getEmploymentType())
                .salaryText(jp.getSalaryText())
                .applicationStartsAt(DateTimeUtils.toKstOffsetDateTime(jp.getApplicationStartsAt()))
                .applicationEndsAt(DateTimeUtils.toKstOffsetDateTime(jp.getApplicationEndsAt()))
                .postingType(jp.getPostingType())
                .reviewStatus(jp.getReviewStatus())
                .postingStatus(jp.getPostingStatus())
                .isScrapped(false)
                .build();
    }
}