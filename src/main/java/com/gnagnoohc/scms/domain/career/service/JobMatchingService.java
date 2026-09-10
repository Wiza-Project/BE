package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.career.repository.JobPreferenceRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
    private final JobPreferenceRepository jobPreferenceRepository;
    private final ConsentVerifier consentVerifier;

    /**
     * [학생] 맞춤 추천 채용공고 목록 조회
     * <li>PROFILING 미동의 시: 빈 목록 반환 (FE에서 동의 유도 UI 노출)</li>
     * <li>취업희망조건(NCS 벡터) 미등록 시: 빈 목록 반환 (FE에서 희망조건 설정 유도 UI 노출)</li>
     * <li>정상 조건 충족 시: 코사인 유사도 Top-10 공고 반환 (결과 0건 시 최신 활성 공고 Fallback)</li>
     */
    public List<JobPostingSummaryResponseDTO> getRecommendedPostingsForStudent(Integer studentUserId) {
        Instant now = Instant.now();

        // 1. 개인정보 PROFILING 동의 여부 확인 0910
        boolean hasProfilingConsent = consentVerifier.hasValidConsent(
                studentUserId, ConsentModuleCode.CAREER, ConsentType.PROFILING, now);

        if (!hasProfilingConsent) {
            log.debug("[JobMatchingService] 학생(userId: {}) PROFILING 미동의 상태", studentUserId);
            return List.of();
        }

        // AI 맞춤 추천은 CAREER 모듈의 PROFILING 선택 동의 검사
        // (과도기 화면에서 수집된 THIRD_PARTY_SHARE도 함께 허용)
//        boolean hasConsent = consentVerifier.hasValidConsent(
//                studentUserId, ConsentModuleCode.CAREER, ConsentType.PROFILING, now)
//                || consentVerifier.hasValidConsent(
//                studentUserId, ConsentModuleCode.CAREER, ConsentType.THIRD_PARTY_SHARE, now)
//                || consentVerifier.hasValidConsent(
//                studentUserId, ConsentModuleCode.COMMON, ConsentType.THIRD_PARTY_SHARE, now);
//
//        if (!hasConsent) {
//            log.debug("[JobMatchingService] 학생(userId: {}) 맞춤 추천 동의 미완료 상태", studentUserId);
//            return List.of();
//        }


        // 2. 학생 벡터 조회
        StudentProfile profile = studentProfileRepository.findByUserId(studentUserId).orElse(null);
        if (profile == null || profile.getEmbeddingVector() == null || profile.getEmbeddingVector().length == 0) {
            log.debug("[JobMatchingService] 학생(userId: {}) 벡터 부재로 빈 공고 반환", studentUserId);
            return List.of();
        }

        // 3. PostgreSQL vector 문자열 변환 후 코사인 유사도 매칭 실행
//        String vectorString = Arrays.toString(profile.getEmbeddingVector());
//        List<JobPosting> matchedPostings = jobPostingRepository.findVectorRecommendedPostings(
//                vectorString, topKMatchLimit, now
//        );
//
//        if (matchedPostings.isEmpty()) {
//            return getFallbackPostings(now);
//        }
//
//        return matchedPostings.stream()
//                .map(this::convertToSummaryDTO)
//                .toList();
        // 3. 학생 취업 희망조건 조회 (지역, 선호 고용형태) 0910
        Optional<JobPreference> preferenceOpt = jobPreferenceRepository.findByStudent_UserId(studentUserId);
        Integer preferredRegionId = preferenceOpt
                .filter(p -> p.getRegionCode() != null)
                .map(p -> p.getRegionCode().getCodeId())
                .orElse(null);
        String preferredEmploymentType = preferenceOpt
                .map(JobPreference::getPreferredEmploymentType)
                .orElse(null);

        String vectorString = Arrays.toString(profile.getEmbeddingVector());

        // 4. 후보군 추출 (1차: 희망지역 우선 조회) 0910
        int candidatePoolSize = topKMatchLimit * 2;
        List<JobPosting> candidates = new ArrayList<>(
                jobPostingRepository.findVectorRecommendedPostings(vectorString, preferredRegionId, candidatePoolSize, now)
        );

        // 5. 지역 공고가 부족할 경우 전국 단위 공고로 후보군 보충 (Fallback 방어) 0910
        if (candidates.size() < topKMatchLimit && preferredRegionId != null) {
            List<JobPosting> fallbackNationwide = jobPostingRepository.findVectorRecommendedPostings(
                    vectorString, null, candidatePoolSize, now
            );
            Set<Integer> existingIds = new HashSet<>(candidates.stream().map(JobPosting::getJobPostingId).toList());
            for (JobPosting fb : fallbackNationwide) {
                if (!existingIds.contains(fb.getJobPostingId())) {
                    candidates.add(fb);
                }
            }
        }

        if (candidates.isEmpty()) {
            return getFallbackPostings(now);
        }

        // 6. [하이브리드 랭킹 엔진] 종합 가중치 스코어링 & 정렬 0910
        return candidates.stream()
                .map(posting -> new ScoredPosting(
                        posting,
                        calculateScore(posting, preferredRegionId, preferredEmploymentType, now)
                ))
                .sorted(Comparator.comparingDouble(ScoredPosting::score).reversed())
                .limit(topKMatchLimit)
                .map(sp -> convertToSummaryDTO(sp.posting()))
                .toList();


    }

    /** 0910
     * 하이브리드 가중치 점수 계산
     * - 기본점수: 50.0 (NCS 직무 매칭 통과분)
     * - 지역 일치 가산점: +35.0 (타 지역 공고 유입 원천 방어)
     * - 고용형태 일치 가산점: +10.0 (정규직 등)
     * - 마감 임박 가산점: 최대 +5.0 (지원 유도)
     */
    private double calculateScore(JobPosting posting, Integer preferredRegionId, String preferredEmploymentType, Instant now) {
        double score = 50.0;

        if (preferredRegionId != null && posting.getRegionCode() != null
                && preferredRegionId.equals(posting.getRegionCode().getCodeId())) {
            score += 35.0;
        }

        if (preferredEmploymentType != null && preferredEmploymentType.equalsIgnoreCase(posting.getEmploymentType())) {
            score += 10.0;
        }

        if (posting.getApplicationEndsAt() != null) {
            long daysLeft = Duration.between(now, posting.getApplicationEndsAt()).toDays();
            if (daysLeft >= 0 && daysLeft <= 7) {
                score += (7 - daysLeft) * 0.7;
            }
        }

        return score;
    }
    private record ScoredPosting(JobPosting posting, double score) {}

    /**
     * PROFILING 미동의 / 벡터 부재 / 매칭 결과 0건 시 Fallback 기본 최신 공고 반환
     */
    private List<JobPostingSummaryResponseDTO> getFallbackPostings(Instant now) {
        List<JobPosting> activePostings = jobPostingRepository.findDefaultActivePostingsWithDetails(now);

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