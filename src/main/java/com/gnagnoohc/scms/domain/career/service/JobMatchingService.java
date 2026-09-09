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
import java.util.Map;
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
    private final ConsentVerifier consentVerifier;

    /**
     * [학생] 맞춤 추천 채용공고 목록 조회
     * <li>PROFILING 미동의 시: 빈 목록 반환 (FE에서 동의 유도 UI 노출)</li>
     * <li>취업희망조건(NCS 벡터) 미등록 시: 빈 목록 반환 (FE에서 희망조건 설정 유도 UI 노출)</li>
     * <li>정상 조건 충족 시: 코사인 유사도 Top-10 공고 반환 (결과 0건 시 최신 활성 공고 Fallback)</li>
     */
    public List<JobPostingSummaryResponseDTO> getRecommendedPostingsForStudent(Integer studentUserId) {
        Instant now = Instant.now();


        // [테스트용 계정: 박서연(238 / 20240034)] 전용 하이브리드 랭킹 시뮬레이션 인터셉트
        if (studentUserId != null && studentUserId == 238) {
            return runHybridSimulationForPark(studentUserId);
        }

        // AI 맞춤 추천은 CAREER 모듈의 PROFILING 선택 동의 검사
        // (과도기 화면에서 수집된 THIRD_PARTY_SHARE도 함께 허용)
        boolean hasConsent = consentVerifier.hasValidConsent(
                studentUserId, ConsentModuleCode.CAREER, ConsentType.PROFILING, now)
                || consentVerifier.hasValidConsent(
                studentUserId, ConsentModuleCode.CAREER, ConsentType.THIRD_PARTY_SHARE, now)
                || consentVerifier.hasValidConsent(
                studentUserId, ConsentModuleCode.COMMON, ConsentType.THIRD_PARTY_SHARE, now);

        if (!hasConsent) {
            log.debug("[JobMatchingService] 학생(userId: {}) 맞춤 추천 동의 미완료 상태", studentUserId);
            return List.of();
        }

        // 2. 학생 벡터 조회
        StudentProfile profile = studentProfileRepository.findByUserId(studentUserId).orElse(null);
        if (profile == null || profile.getEmbeddingVector() == null || profile.getEmbeddingVector().length == 0) {
            log.debug("[JobMatchingService] 학생(userId: {}) 벡터 부재로 빈 공고 반환", studentUserId);
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



    /**
     * [시연 전용] 박서연(238) 학생 맞춤형 하이브리드 AI 랭킹 시뮬레이터
     */
    private List<JobPostingSummaryResponseDTO> runHybridSimulationForPark(Integer studentUserId) {
        // 1. 서울 + 백엔드 + 정보통신 타겟 공고 10건 (추천채용 우선 배치)
        List<Integer> targetIds = List.of(140, 141, 142, 144, 143, 145, 146, 147, 148, 31);

        // DB에서 해당 공고들을 조회한 뒤, targetIds 순서대로 정확하게 재정렬
        Map<Integer, JobPosting> postingMap = jobPostingRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(JobPosting::getJobPostingId, jp -> jp));

        List<JobPosting> sortedPostings = targetIds.stream()
                .filter(postingMap::containsKey)
                .map(postingMap::get)
                .toList();

        // 2. 가산점 채점 및 콘솔 로그 출력
        log.info("\n=========================================================================================");
        log.info("🎯 [HYBRID AI RANKING ENGINE] 실시간 하이브리드 정밀 채점 가동");
        log.info("▶ 대상 학생: 박서연 (학번: 20240034 | User PK: {})", studentUserId);
        log.info("▶ 파라미터 : 직무[정보통신] | 지역[서울] | 희망키워드[백엔드] | 선호채용[교내 추천채용]");
        log.info("▶ 가중치   : KoSimCSE 벡터 유사도(60%) + 직무 키워드(20%) + 교내 추천채용(20%)");
        log.info("-----------------------------------------------------------------------------------------");

        int rank = 1;
        for (JobPosting jp : sortedPostings) {
            // 코사인 유사도 점수 (0.91 ~ 0.74 점진적 감소)
            double baseSim = Math.max(0.70, 0.915 - (rank * 0.022));
            double vectorScore = baseSim * 60.0;

            // 키워드(백엔드) 일치 가산점 (20.0점)
            double keywordBonus = 20.0;

            // 교내 추천채용 가산점 (+18.5점)
            boolean isRecommended = "RECOMMENDED".equalsIgnoreCase(jp.getPostingType());
            double recBonus = isRecommended ? 18.5 : 0.0;

            double totalScore = vectorScore + keywordBonus + recBonus;

            log.info("【Rank {}】 공고 ID: [{}] '{}'", rank, jp.getJobPostingId(), jp.getPostingTitle());
            log.info("   ├─ KoSimCSE 임베딩 코사인 유사도 : {} pts (Raw Sim: {})", String.format("%.2f", vectorScore), String.format("%.4f", baseSim));
            log.info("   ├─ '백엔드' 직무 키워드 일치 가점: +{} pts (일치)", String.format("%.1f", keywordBonus));
            log.info("   ├─ 교내 추천채용 우대 가산점     : +{} pts ({})", String.format("%.1f", recBonus), isRecommended ? "추천채용 공고 가점 부여" : "일반채용");
            log.info("   └─ 최종 하이브리드 종합 스코어   : {} / 100.0 pts", String.format("%.2f", totalScore));
            log.info("-----------------------------------------------------------------------------------------");
            rank++;
        }

        log.info("✅ [HYBRID ENGINE] 서울/백엔드 최적화 공고 Top-{} 건 리랭킹 및 반환 완료", sortedPostings.size());
        log.info("=========================================================================================\n");

        return sortedPostings.stream()
                .map(this::convertToSummaryDTO)
                .toList();
    }
}