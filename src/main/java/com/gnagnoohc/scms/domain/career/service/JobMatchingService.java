package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
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

    private final JobPostingRepository jobPostingRepository;
    private final JobPreferenceRepository jobPreferenceRepository;
    private final ConsentVerifier consentVerifier;

    /**
     * [학생] pgvector 코사인 유사도 연산 기반 맞춤 추천 채용공고 목록 조회
     *
     * <p><strong>[처리 흐름]</strong></p>
     * <ul>
     *   <li>1. AI 맞춤 추천(PROFILING) 선택 동의 여부 검증 (미동의 시 기본 최신 공고 서빙)</li>
     *   <li>2. 학생 프로필(student_profile)의 임베딩 벡터 로드</li>
     *   <li>3. pgvector 코사인 거리 연산자(&lt;=&gt;)를 통한 상위 유사 직무 채용공고 조회</li>
     *   <li>4. 매칭 결과 부재 또는 프로필 미설정 시 최신 유효 공고 Fallback 반환</li>
     * </ul>
     */
    public List<JobPostingSummaryResponseDTO> getRecommendedPostingsForStudent(Integer studentUserId) {
        Instant now = Instant.now();

        // 1. AI 맞춤 추천(PROFILING) 선택 동의 여부 확인
        boolean hasProfilingConsent = consentVerifier.hasValidConsent(
                studentUserId, ConsentModuleCode.CAREER, ConsentType.PROFILING, now);

        if (!hasProfilingConsent) {
            return getFallbackPostings(now);
        }

        // 2. 학생의 임베딩 벡터 조회
        StudentProfile profile = studentProfileRepository.findByUserId(studentUserId).orElse(null);
        if (profile == null || profile.getEmbeddingVector() == null || profile.getEmbeddingVector().length == 0) {
            log.debug("[JobMatchingService] 학생(userId: {})의 임베딩 벡터가 존재하지 않아 기본 공고를 반환합니다.", studentUserId);
            return getFallbackPostings(now);
        }

        // 3. float[] -> PostgreSQL vector 문자열 포맷 변환 (예: "[0.123, -0.456, ...]")
        String vectorString = Arrays.toString(profile.getEmbeddingVector());

        // 4. pgvector 코사인 유사도 기반 상위 추천 공고 조회
        List<JobPosting> matchedPostings = jobPostingRepository.findVectorRecommendedPostings(
                vectorString, TOP_K_MATCH_LIMIT, now
        );

        if (matchedPostings.isEmpty()) {
            return getFallbackPostings(now);
        }

        return matchedPostings.stream()
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