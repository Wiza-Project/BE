package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.request.CompetencyAiChatRequest;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse.CompetencyResult;
import com.gnagnoohc.scms.domain.competency.dto.response.CompetencyAiChatResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.RecommendedProgramsResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.RecommendedProgramsResponse.RecommendedProgram;
import com.gnagnoohc.scms.domain.competency.dto.response.RecommendedProgramsResponse.WeakCompetencyGroup;
import com.gnagnoohc.scms.domain.competency.support.WeakCompetencySelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 제출 완료된 핵심역량 결과를 LLM 설명과 결합한다.
 *
 * <p>점수 계산·본인 응시 소유권·취약역량 선정·모집중 프로그램 조회는 기존 서비스를 재사용한다.
 * LLM은 결과를 재계산하거나 프로그램을 새로 고르지 않고, 서버가 확정한 사실을 자연어로 설명한다.</p>
 *
 * <p>클래스 단위 {@code @Transactional}을 두지 않는다. LLM 호출은 수 초가 걸리는 외부 HTTP 요청이라
 * 트랜잭션 안에서 부르면 응답을 기다리는 동안 DB 커넥션을 붙들게 된다. 조회는 재사용하는 각 서비스가
 * 자기 읽기 전용 트랜잭션 안에서 끝낸다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetencyAiAssistantService {

    private static final int MAX_MESSAGE_LENGTH = 3_000;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AssessmentResultService assessmentResultService;
    private final AssessmentRecommendationService assessmentRecommendationService;
    private final WeakCompetencySelector weakCompetencySelector;

    public CompetencyAiChatResponse chat(Integer studentId, CompetencyAiChatRequest request) {
        String message = trimMessage(request.message());
        AssessmentResultResponse result = assessmentResultService.getResult(request.attemptId(), studentId);
        List<CompetencyResult> weakCompetencies = weakCompetencySelector.select(result.scores());
        RecommendedProgramsResponse recommendedPrograms =
                assessmentRecommendationService.recommend(request.attemptId(), studentId);

        List<CompetencyAiChatResponse.CompetencyInsight> insights = weakCompetencies.stream()
                .map(this::toInsight)
                .toList();
        List<CompetencyAiChatResponse.ProgramRecommendation> programs =
                toProgramRecommendations(recommendedPrograms);
        List<String> nextActions = buildNextActions(insights, programs);

        return new CompetencyAiChatResponse(
                generateReply(message, result, insights, programs, nextActions),
                new CompetencyAiChatResponse.ResultSummary(
                        result.attemptId(),
                        result.overallAverageScore(),
                        result.submittedAt(),
                        result.percentileAvailable()
                ),
                insights,
                programs,
                nextActions
        );
    }

    private CompetencyAiChatResponse.CompetencyInsight toInsight(CompetencyResult score) {
        BigDecimal convertedScore = score.convertedScore();
        int numericScore = convertedScore == null ? 0 : convertedScore.intValue();
        String judgment = numericScore >= 75 ? "우수" : numericScore >= 60 ? "보통" : "보완 필요";
        String recommendation = numericScore < 60
                ? "관련 프로그램이나 실습을 통해 기초 역량을 반복적으로 연습해 보세요."
                : numericScore < 75
                ? "실제 과제나 비교과 활동에서 해당 역량을 적용해 보세요."
                : "현재 강점을 다른 역량과 연결해 프로젝트 성과로 확장해 보세요.";
        return new CompetencyAiChatResponse.CompetencyInsight(
                score.competencyId(),
                score.competencyName(),
                convertedScore,
                judgment,
                recommendation
        );
    }

    private List<CompetencyAiChatResponse.ProgramRecommendation> toProgramRecommendations(
            RecommendedProgramsResponse response
    ) {
        return response.weakCompetencies().stream()
                .flatMap(group -> group.programs().stream()
                        .map(program -> toProgramRecommendation(group, program)))
                .toList();
    }

    private CompetencyAiChatResponse.ProgramRecommendation toProgramRecommendation(
            WeakCompetencyGroup group,
            RecommendedProgram program
    ) {
        return new CompetencyAiChatResponse.ProgramRecommendation(
                program.programId(),
                program.programName(),
                group.competencyName(),
                program.programTypeName(),
                program.recruitmentEndsAt(),
                program.remainingCapacity(),
                program.myApplicationStatusLabel()
        );
    }

    private List<String> buildNextActions(
            List<CompetencyAiChatResponse.CompetencyInsight> insights,
            List<CompetencyAiChatResponse.ProgramRecommendation> programs
    ) {
        if (insights.isEmpty()) {
            return List.of("현재 결과에서 보완 대상 역량을 확인하지 못했습니다. 진단 결과를 다시 확인해 주세요.");
        }
        List<String> actions = new ArrayList<>();
        actions.add(insights.get(0).competencyName() + " 역량의 낮은 세부 점수부터 보완 목표로 정하세요.");
        if (!programs.isEmpty()) {
            actions.add("추천 비교과 프로그램의 모집 기간과 잔여 정원을 확인해 보세요.");
        } else {
            actions.add("현재 연결된 모집중 프로그램이 없으므로 다음 비교과 모집 공지를 확인해 보세요.");
        }
        actions.add("다음 진단에서 같은 역량의 변화량을 비교해 보세요.");
        return actions;
    }

    private String generateReply(
            String message,
            AssessmentResultResponse result,
            List<CompetencyAiChatResponse.CompetencyInsight> insights,
            List<CompetencyAiChatResponse.ProgramRecommendation> programs,
            List<String> nextActions
    ) {
        try {
            ChatClient chatClient = chatClient();
            if (chatClient == null) {
                return fallbackReply(insights, programs);
            }

            String scoreContext = result.scores().isEmpty()
                    ? "점수 없음"
                    : result.scores().stream()
                    .sorted(Comparator.comparing(
                            AssessmentResultResponse.CompetencyResult::displayOrder,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(score -> "%s=%s점".formatted(score.competencyName(), score.convertedScore()))
                    .collect(Collectors.joining(", "));
            String programContext = programs.isEmpty()
                    ? "현재 모집중 추천 프로그램 없음"
                    : programs.stream()
                    .map(program -> "%s(역량=%s, 모집마감=%s, 잔여=%d)".formatted(
                            program.programName(),
                            program.competencyName(),
                            program.recruitmentEndsAt(),
                            program.remainingCapacity()
                    ))
                    .collect(Collectors.joining("\n"));

            String content = chatClient.prompt()
                    .system("""
                            당신은 대학생의 핵심역량 진단 결과를 설명하는 학습 코치다.
                            한국어로 간결하고 구체적으로 답변하라.
                            제공된 진단 점수와 서버 추천 프로그램만 사용하고, 점수·프로그램·성격을 만들어내지 말라.
                            결과를 의료적 진단이나 사람의 능력에 대한 확정적 평가로 표현하지 말라.
                            사용자의 질문에 답하고, 가장 보완할 역량과 바로 실행할 행동을 2~4개 단락으로 안내하라.
                            """)
                    .user("""
                            사용자 질문: %s
                            전체 평균: %s
                            역량별 점수: %s
                            서버가 선정한 보완 역량:
                            %s
                            서버가 확인한 추천 프로그램:
                            %s
                            다음 행동 후보:
                            %s
                            """.formatted(
                            message,
                            result.overallAverageScore(),
                            scoreContext,
                            insights,
                            programContext,
                            nextActions
                    ))
                    .call()
                    .content();
            if (StringUtils.hasText(content)) {
                return content.trim();
            }
        } catch (Exception e) {
            log.warn("핵심역량 AI 답변 생성에 실패하여 기본 답변으로 전환합니다.", e);
        }
        return fallbackReply(insights, programs);
    }

    private String fallbackReply(
            List<CompetencyAiChatResponse.CompetencyInsight> insights,
            List<CompetencyAiChatResponse.ProgramRecommendation> programs
    ) {
        if (insights.isEmpty()) {
            return "현재 진단 결과에서 보완 역량을 확인하지 못했습니다. 결과를 다시 조회해 주세요.";
        }
        String weakest = insights.get(0).competencyName();
        if (programs.isEmpty()) {
            return "현재 결과에서는 " + weakest
                    + " 역량을 우선 보완 대상으로 볼 수 있습니다. 연결된 모집중 프로그램은 없으므로 관련 활동과 다음 진단 변화를 함께 확인해 보세요.";
        }
        return "현재 결과에서는 " + weakest
                + " 역량을 우선 보완 대상으로 볼 수 있습니다. 연결된 모집중 프로그램을 확인하고, 모집 마감일과 잔여 정원을 살펴본 뒤 참여 여부를 결정해 보세요.";
    }

    private ChatClient chatClient() {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        return builder == null ? null : builder.build();
    }

    private String trimMessage(String message) {
        String trimmed = message == null ? "" : message.trim();
        return trimmed.length() <= MAX_MESSAGE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_MESSAGE_LENGTH);
    }
}
