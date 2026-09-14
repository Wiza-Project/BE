package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
import com.gnagnoohc.scms.domain.career.helper.TechKeywordDictionary;
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
    private final TechKeywordDictionary techKeywordDictionary;

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
        Integer preferredNcsId = preferenceOpt
                .filter(p -> p.getNcsCode() != null)
                .map(p -> p.getNcsCode().getCodeId())
                .orElse(null);
        Integer preferredRegionId = preferenceOpt
                .filter(p -> p.getRegionCode() != null)
                .map(p -> p.getRegionCode().getCodeId())
                .orElse(null);
        String preferredEmploymentType = preferenceOpt
                .map(JobPreference::getPreferredEmploymentType)
                .orElse(null);
        // 하이브리드 랭킹 엔진용 추가 _ 선호 채용구분 및 키워드 추출
        String preferredPostingType = preferenceOpt
                .map(JobPreference::getPreferredPostingType)
                .orElse(null);
        String jobKeyword = preferenceOpt
                .map(JobPreference::getJobKeyword)
                .orElse(null);

        String vectorString = Arrays.toString(profile.getEmbeddingVector());

        // 4. 후보군 추출: 학생 희망 직무(NCS)를 필수로 강제하여 조회
        int candidatePoolSize = topKMatchLimit * 2;
        List<JobPosting> candidates = new ArrayList<>(
                jobPostingRepository.findVectorRecommendedPostings(vectorString, preferredRegionId, preferredNcsId, candidatePoolSize, now)
        );

        // 5. 지역 공고가 부족할 경우 전국 단위 공고로 후보군 보충 (Fallback 방어) 0910
        if (candidates.size() < topKMatchLimit && preferredRegionId != null) {
            List<JobPosting> fallbackNationwide = jobPostingRepository.findVectorRecommendedPostings(
                    vectorString, null, preferredNcsId, candidatePoolSize, now
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

        // 6. N+1 방지: ID 목록으로 연관 엔티티(회사, 지역, 직무) 일괄 Fetch Join
        List<Integer> candidateIds = candidates.stream().map(JobPosting::getJobPostingId).toList();
        List<JobPosting> loadedCandidates = jobPostingRepository.findAllByIdsWithDetails(candidateIds);

        // 7. [하이브리드 랭킹 엔진] 종합 가중치 스코어링 & 정렬 0914
        List<ScoredPosting> scoredPostings = loadedCandidates.stream()
                .map(posting -> new ScoredPosting(
                        posting,
                        calculateScore(posting, preferredRegionId, preferredEmploymentType, preferredPostingType, jobKeyword, now)
                ))
                .sorted(Comparator.comparingDouble((ScoredPosting sp) -> sp.detail().totalScore()).reversed())
                .limit(topKMatchLimit)
                .toList();

        // 8. [콘솔 채점표 출력] 실제 가산점 적용 내역 확인
        // 8. [콘솔 채점표 및 매칭 검증 상세 출력]
        String ncsInfo = preferenceOpt.filter(p -> p.getNcsCode() != null)
                .map(p -> p.getNcsCode().getCodeName() + " (" + p.getNcsCode().getCode() + ")")
                .orElse("미설정");
        String regionInfo = preferenceOpt.filter(p -> p.getRegionCode() != null)
                .map(p -> p.getRegionCode().getCodeName())
                .orElse("전국");

        log.info("======================================== [AI 하이브리드 추천 랭킹 상세 검증표] ========================================");
        log.info("▶ 학생 ID: {} | 직무(NCS): {} | 지역: {} | 고용형태: {} | 채용구분: {}",
                studentUserId, ncsInfo, regionInfo,
                preferredEmploymentType != null ? preferredEmploymentType : "무관",
                preferredPostingType != null ? preferredPostingType : "무관");
        log.info("▶ 등록된 키워드: {}", (jobKeyword != null && !jobKeyword.isBlank()) ? "[" + jobKeyword + "]" : "없음");
        log.info("-----------------------------------------------------------------------------------------------------------------------------------------");

        for (int i = 0; i < scoredPostings.size(); i++) {
            ScoredPosting sp = scoredPostings.get(i);
            JobPosting jp = sp.posting();
            ScoreDetail d = sp.detail();

            String jobRegion = jp.getRegionCode() != null ? jp.getRegionCode().getCodeName() : "전국";
            String matchedKwStr = d.matchedKeywords().isEmpty()
                    ? "일치 없음"
                    : String.join(", ", d.matchedKeywords()) + " (" + d.matchedKeywords().size() + "/" + d.totalKeywordCount() + "개 일치)";

            log.info(String.format("[%2d위] 공고 #%-4d | 총점: %5.1f점 | [기본 %2.0f | 지역 +%2.0f | 고용 +%2.0f | 구분 +%2.0f | 키워드 +%2.0f | 마감 +%3.1f]",
                    (i + 1),
                    jp.getJobPostingId(),
                    d.totalScore(),
                    d.baseScore(),
                    d.regionBonus(),
                    d.jobTypeBonus(),
                    d.postingTypeBonus(),
                    d.keywordBonus(),
                    d.deadlineBonus()
            ));
            log.info(String.format("      └ 공고정보: [%s] [%s] [%s] %s",
                    jobRegion,
                    jp.getEmploymentType() != null ? jp.getEmploymentType() : "고용무관",
                    jp.getPostingType() != null ? jp.getPostingType() : "일반",
                    jp.getPostingTitle()
            ));
            log.info(String.format("      └ 키워드 검증: %s -> 가산점 +%.0f점", matchedKwStr, d.keywordBonus()));
            log.info("-----------------------------------------------------------------------------------------------------------------------------------------");
        }
        log.info("=========================================================================================================================================");

        return scoredPostings.stream()
                .map(sp -> convertToSummaryDTO(sp.posting()))
                .toList();

    }

    /**
     * 하이브리드 가중치 세부 채점 로직
     * - 기본점수: 50.0 (NCS 직무 일치 통과분)
     * - 지역 일치 가산점: +35.0
     * - 고용형태 일치 가산점: +10.0
     * - 마감 임박 가산점: 최대 +5.0 (7일 이내 마감 임박 시 일자별 차등)
     */
    /**
     * 하이브리드 가중치 세부 채점 로직 (다중 키워드 개수별 차등 가산점 적용)
     */
    private ScoreDetail calculateScore(JobPosting posting, Integer preferredRegionId,
                                       String preferredEmploymentType, String preferredPostingType,
                                       String jobKeyword, Instant now) {
        double baseScore = 50.0;
        double regionBonus = 0.0;
        double jobTypeBonus = 0.0;
        double postingTypeBonus = 0.0;
        double keywordBonus = 0.0;
        double deadlineBonus = 0.0;

        List<String> matchedKeywords = new ArrayList<>();
        int totalKeywordCount = 0;

        // 1) 희망 지역 일치 가산점 (+35점)
        if (preferredRegionId != null && posting.getRegionCode() != null
                && preferredRegionId.equals(posting.getRegionCode().getCodeId())) {
            regionBonus = 35.0;
        }

        // 2) 선호 고용형태 일치 가산점 (+10점)
        if (preferredEmploymentType != null && preferredEmploymentType.equalsIgnoreCase(posting.getEmploymentType())) {
            jobTypeBonus = 10.0;
        }

        // 3) 선호 채용 구분 일치 가산점 (+10점)
        if (preferredPostingType != null && preferredPostingType.equalsIgnoreCase(posting.getPostingType())) {
            postingTypeBonus = 10.0;
        }

        // 4) 희망 직무 키워드 매칭 (일치 단어당 +5점, 최대 +15점 상한)
        if (jobKeyword != null && !jobKeyword.isBlank()) {
            String title = posting.getPostingTitle() != null ? posting.getPostingTitle().toLowerCase() : "";
            String desc = posting.getJobDescription() != null ? posting.getJobDescription().toLowerCase() : "";

            String[] tokens = jobKeyword.split("[,\\s]+");
            List<String> validTokens = Arrays.stream(tokens)
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .distinct()
                    .toList();

            totalKeywordCount = validTokens.size();

//            for (String token : validTokens) {
//                String target = token.toLowerCase();
//                if (title.contains(target) || desc.contains(target)) {
//                    matchedKeywords.add(token);
//                }
//            }

            for (String token : validTokens) {
                // 동의어 사전 컴포넌트 호출
                Set<String> searchPool = techKeywordDictionary.getSearchPool(token);
                boolean matched = searchPool.stream().anyMatch(kw -> title.contains(kw) || desc.contains(kw));

                if (matched) {
                    matchedKeywords.add(token);
                }
            }

            // 개당 5점 부여 (최대 15점)
            keywordBonus = Math.min(matchedKeywords.size() * 5.0, 15.0);
        }

        // 5) 마감 임박 가산점 (7일 이내 마감 시 최대 +4.9점)
        if (posting.getApplicationEndsAt() != null) {
            long daysLeft = Duration.between(now, posting.getApplicationEndsAt()).toDays();
            if (daysLeft >= 0 && daysLeft <= 7) {
                deadlineBonus = (7 - daysLeft) * 0.7;
            }
        }

        double totalScore = baseScore + regionBonus + jobTypeBonus + postingTypeBonus + keywordBonus + deadlineBonus;
        return new ScoreDetail(
                totalScore, baseScore, regionBonus, jobTypeBonus,
                postingTypeBonus, keywordBonus, deadlineBonus,
                matchedKeywords, totalKeywordCount
        );
    }

    private record ScoreDetail(
            double totalScore, double baseScore, double regionBonus,
            double jobTypeBonus, double postingTypeBonus, double keywordBonus, double deadlineBonus,
            List<String> matchedKeywords, int totalKeywordCount
    ) {}
    private record ScoredPosting(JobPosting posting, ScoreDetail detail) {}

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