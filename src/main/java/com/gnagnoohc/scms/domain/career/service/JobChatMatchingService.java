package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.posting.JobChatCriteriaDTO;
import com.gnagnoohc.scms.domain.career.dto.posting.JobChatResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 자연어 조건 추출과 DB 기반 채용공고 3건 추천을 담당한다. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobChatMatchingService {

    private static final int CANDIDATE_LIMIT = 20;
    private static final int RECOMMENDATION_LIMIT = 3;

    private final JobPostingRepository jobPostingRepository;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;

    @Value("${spring.ai.model.chat:unset}")
    private String configuredChatModel;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    private final AtomicBoolean providerLogged = new AtomicBoolean(false);

    public JobChatResponseDTO chat(String message) {
        JobChatCriteriaDTO criteria = extractCriteria(message);
        List<JobPosting> candidates = findCandidates(criteria);
        List<JobPostingSummaryResponseDTO> recommendations = candidates.stream()
                .limit(RECOMMENDATION_LIMIT)
                .map(this::toSummary)
                .toList();

        return new JobChatResponseDTO(
                generateAnswer(message, criteria, recommendations),
                criteria,
                recommendations
        );
    }

    private JobChatCriteriaDTO extractCriteria(String message) {
        ChatClient chatClient = chatClient();
        if (chatClient != null) {
            try {
                JobChatCriteriaDTO criteria = chatClient.prompt()
                        .system("""
                                당신은 한국 채용공고 검색 조건 추출기다.
                                사용자의 문장에서 지역, NCS 직무 또는 직무명, 고용형태, 핵심 키워드를 추출하라.
                                모르는 값은 null 또는 빈 배열로 두고, 답변 문장은 만들지 말라.
                                keywords에는 Java, Spring, 백엔드처럼 DB 검색에 유용한 짧은 단어만 최대 6개 넣어라.
                                반드시 JobChatCriteriaDTO JSON 형식으로 반환하라.
                                """)
                        .user(message)
                        .call()
                        .entity(JobChatCriteriaDTO.class);
                if (criteria != null) {
                    return criteria;
                }
            } catch (Exception e) {
                log.warn("LLM 조건 추출에 실패하여 규칙 기반 추출로 전환합니다.", e);
            }
        }
        return fallbackCriteria(message);
    }

    private List<JobPosting> findCandidates(JobChatCriteriaDTO criteria) {
        Map<Integer, JobPosting> unique = new LinkedHashMap<>();
        List<String> keywords = criteria.getKeywords().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(6)
                .toList();

        if (keywords.isEmpty()) {
            addCandidates(unique, criteria, null);
        } else {
            for (String keyword : keywords) {
                addCandidates(unique, criteria, keyword);
            }
        }

        if (unique.size() < RECOMMENDATION_LIMIT) {
            addCandidates(unique, criteria, null);
        }
        if (unique.size() < RECOMMENDATION_LIMIT) {
            addCandidates(unique, new JobChatCriteriaDTO(), null);
        }
        return new ArrayList<>(unique.values()).stream().limit(CANDIDATE_LIMIT).toList();
    }

    private void addCandidates(Map<Integer, JobPosting> unique, JobChatCriteriaDTO criteria, String keyword) {
        jobPostingRepository.findChatCandidates(
                        normalized(criteria.getRegion()),
                        normalized(criteria.getNcs()),
                        normalized(criteria.getEmploymentType()),
                        normalized(keyword),
                        Instant.now(),
                        PageRequest.of(0, CANDIDATE_LIMIT))
                .forEach(posting -> unique.putIfAbsent(posting.getJobPostingId(), posting));
    }

    private String generateAnswer(String message, JobChatCriteriaDTO criteria,
                                  List<JobPostingSummaryResponseDTO> recommendations) {
        if (recommendations.isEmpty()) {
            return "조건에 맞는 현재 게시 중인 채용공고를 찾지 못했습니다. 지역이나 직무 조건을 조금 넓혀서 다시 요청해 주세요.";
        }

        ChatClient chatClient = chatClient();
        if (chatClient == null) {
            return fallbackAnswer(criteria, recommendations);
        }

        try {
            String candidates = recommendations.stream()
                    .map(item -> String.format(
                            "공고ID=%s, 회사=%s, 제목=%s, 직무=%s, 지역=%s, 고용형태=%s, 급여=%s, 마감=%s",
                            item.getJobPostingId(),
                            item.getCompanyName(),
                            item.getPostingTitle(),
                            item.getNcsCodeName(),
                            item.getRegionCodeName(),
                            item.getEmploymentType(),
                            item.getSalaryText(),
                            item.getApplicationEndsAt()))
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            return chatClient.prompt()
                    .system("""
                            당신은 취업 채용공고 상담사다.
                            아래 후보 목록에 있는 공고만 사용해 한국어로 답변하라.
                            후보 목록 밖의 회사, 직무, 급여, 마감일을 만들어내지 말라.
                            반드시 최대 3건을 번호 목록으로 추천하고, 각 공고를 추천한 이유를 한 문장으로 설명하라.
                            마지막에 사용자가 추가로 말하면 좋은 조건을 한 문장으로 안내하라.
                            """)
                    .user("사용자 요청: " + message + "\n추출 조건: " + criteria.getKeywords()
                            + "\nDB 후보 목록(JSON):\n" + candidates)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("LLM 답변 생성에 실패하여 기본 답변으로 전환합니다.", e);
            return fallbackAnswer(criteria, recommendations);
        }
    }

    private String fallbackAnswer(JobChatCriteriaDTO criteria, List<JobPostingSummaryResponseDTO> recommendations) {
        String condition = String.join(", ", criteria.getKeywords());
        if (!StringUtils.hasText(condition)) condition = "입력하신 조건";
        return condition + "에 맞춰 현재 게시 중인 공고 " + recommendations.size()
                + "건을 찾았습니다. 각 공고의 상세 자격요건과 마감일을 확인해 주세요.";
    }

    private ChatClient chatClient() {
        if (providerLogged.compareAndSet(false, true)) {
            String actualChatModels = chatModelProvider.orderedStream()
                    .map(model -> model.getClass().getName())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("NONE");
            log.info(
                    "[JobChat 진단] configuredChatModel={}, actualChatModel={}, openAiApiKeyPresent={}, openAiApiKeyLength={}",
                    configuredChatModel,
                    actualChatModels,
                    StringUtils.hasText(openAiApiKey),
                    openAiApiKey == null ? 0 : openAiApiKey.length()
            );
        }
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        return builder == null ? null : builder.build();
    }

    private JobChatCriteriaDTO fallbackCriteria(String message) {
        JobChatCriteriaDTO criteria = new JobChatCriteriaDTO();
        String lower = message.toLowerCase(Locale.ROOT);
        for (String region : List.of("서울", "경기", "인천", "부산", "대전", "대구", "광주", "울산", "세종", "제주")) {
            if (message.contains(region)) {
                criteria.setRegion(region);
                break;
            }
        }
        for (String employmentType : List.of("정규직", "계약직", "인턴", "아르바이트", "프리랜서")) {
            if (message.contains(employmentType)) {
                criteria.setEmploymentType(employmentType);
                break;
            }
        }
        List<String> keywords = new ArrayList<>();
        for (String keyword : List.of("Java", "Spring", "백엔드", "프론트엔드", "데이터", "AI", "클라우드", "마케팅", "기획", "회계")) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) keywords.add(keyword);
        }
        criteria.setKeywords(keywords);
        return criteria;
    }

    private JobPostingSummaryResponseDTO toSummary(JobPosting posting) {
        return JobPostingSummaryResponseDTO.builder()
                .jobPostingId(posting.getJobPostingId())
                .companyAccountId(posting.getCompanyAccount().getCompanyAccountId())
                .companyName(posting.getCompanyAccount().getCompanyName())
                .ncsCodeName(posting.getNcsCode() == null ? null : posting.getNcsCode().getCodeName())
                .regionCodeName(posting.getRegionCode() == null ? null : posting.getRegionCode().getCodeName())
                .postingTitle(posting.getPostingTitle())
                .employmentType(posting.getEmploymentType())
                .salaryText(posting.getSalaryText())
                .applicationStartsAt(DateTimeUtils.toKstOffsetDateTime(posting.getApplicationStartsAt()))
                .applicationEndsAt(DateTimeUtils.toKstOffsetDateTime(posting.getApplicationEndsAt()))
                .postingType(posting.getPostingType())
                .reviewStatus(posting.getReviewStatus())
                .postingStatus(posting.getPostingStatus())
                .isScrapped(false)
                .build();
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
