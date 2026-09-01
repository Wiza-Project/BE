package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.career.repository.StudentProfileRepository;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
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

    @Value("${app.career.matching.top-k:10}")
    private int topKMatchLimit;

    private final JobPostingRepository jobPostingRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ConsentVerifier consentVerifier;

    /**
     * [학생] 맞춤 추천 채용공고 목록 조회
     * - PROFILING 미동의 시: 빈 목록 반환 (FE에서 동의 유도 UI 노출)
     * - 취업희망조건(NCS 벡터) 미등록 시: 빈 목록 반환 (FE에서 희망조건 설정 유도 UI 노출)
     * - 정상 조건 충족 시: 코사인 유사도 Top-10 공고 반환
     */
    public List<JobPostingSummaryResponseDTO> getRecommendedPostingsForStudent(Integer studentUserId) {
        Instant now = Instant.now();

        // 1. 개인정보 PROFILING 동의 여부 확인
        boolean hasProfilingConsent = consentVerifier.hasValidConsent(
                studentUserId, ConsentModuleCode.CAREER, ConsentType.PROFILING, now);

        if (!hasProfilingConsent) {
            log.debug("[JobMatchingService] 학생(userId: {}) PROFILING 미동의 상태", studentUserId);
            return List.of();
        }

        // 2. 학생 벡터 조회
        StudentProfile profile = studentProfileRepository.findByUserId(studentUserId).orElse(null);
        if (profile == null || profile.getEmbeddingVector() == null || profile.getEmbeddingVector().length == 0) {
            log.debug("[JobMatchingService] 학생(userId: {}) 벡터 부재로 기본 공고 반환", studentUserId);
            return List.of();
        }

        // 3. PostgreSQL vector 문자열 변환 후 코사인 유사도 매칭 실행
        String vectorString = Arrays.toString(profile.getEmbeddingVector());
        List<JobPosting> matchedPostings = jobPostingRepository.findVectorRecommendedPostings(
                vectorString, topKMatchLimit, now
        );

        if (matchedPostings.isEmpty()) {
            return getFallbackPostings(now);
        }

        return matchedPostings.stream()
                .map(this::convertToSummaryDTO)
                .toList();
    }

    /**
     * PROFILING 미동의 / 벡터 부재 / 매칭 결과 0건 시 Fallback 기본 최신 공고 반환
     */
    private List<JobPostingSummaryResponseDTO> getFallbackPostings(Instant now) {
        List<JobPosting> activePostings = jobPostingRepository.findDefaultActivePostingsWithDetails(now);
        if (activePostings.isEmpty()) {
            // 마감일 미지정 공고 포함 전체 PUBLISHED 공고 fallback
            activePostings = jobPostingRepository.findAll().stream()
                    .filter(jp -> "PUBLISHED".equalsIgnoreCase(jp.getPostingStatus()))
                    .sorted((a, b) -> b.getJobPostingId().compareTo(a.getJobPostingId()))
                    .limit(topKMatchLimit)
                    .toList();
        }
        return activePostings.stream()
                .limit(topKMatchLimit)
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