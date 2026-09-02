package com.gnagnoohc.scms.domain.counsel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.counsel.dto.response.StressTestQuestionsResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.StressTestResultResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.StressTestSubmitRequest;
import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestQuestion;
import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestResult;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.PsychologicalTestQuestionRepository;
import com.gnagnoohc.scms.domain.counsel.repository.PsychologicalTestResultRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 체크리스트 13 "스트레스 심리검사"의 문항 조회, 제출·채점, 본인 결과 이력 조회를 담당한다.
 * 활성 검사(STRESS/1)와 점수 구간은 설계 문서에서 확정한 값을 이 서비스의 상수로만 고정한다.
 * DB의 최대 버전이나 생성일로 활성 버전을 추론하지 않는다(배포 순서에 따라 검사 의미가 바뀌는 것을 막기 위해).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StressTestService {

    private static final String TEST_TYPE = "STRESS";
    private static final String TEST_VERSION = "1";
    private static final int QUESTION_COUNT = 11;
    private static final String INSTRUCTION = "각 문항에 현재 상태와 가장 가까운 응답을 선택하세요.";

    private static final List<CanonicalOption> CANONICAL_OPTIONS = List.of(
            new CanonicalOption(0, "전혀 그렇지 않다"),
            new CanonicalOption(1, "그렇지 않다"),
            new CanonicalOption(2, "그렇다"),
            new CanonicalOption(3, "매우 그렇다")
    );

    private final CounselUserRepository counselUserRepository;
    private final AppUserRepository appUserRepository;
    private final PsychologicalTestQuestionRepository psychologicalTestQuestionRepository;
    private final PsychologicalTestResultRepository psychologicalTestResultRepository;
    private final ConsentVerifier consentVerifier;

    /** 문항 조회에는 상담 동의가 필요 없다(개인정보 저장 액션이 아니다). */
    public StressTestQuestionsResponse getQuestions(Integer studentId) {
        ensureActiveStudent(studentId);
        List<PsychologicalTestQuestion> questions = getValidatedActiveQuestions();
        List<StressTestQuestionsResponse.Question> questionDtos = questions.stream()
                .map(q -> new StressTestQuestionsResponse.Question(
                        q.getPsychologicalTestQuestionId().longValue(),
                        q.getQuestionNo(),
                        q.getQuestionText(),
                        toOptionList(q.getResponseOptions())
                ))
                .toList();
        return new StressTestQuestionsResponse(TEST_TYPE, TEST_VERSION, INSTRUCTION, questionDtos);
    }

    /**
     * 설계 6.2의 순서를 그대로 따른다: 활성 학생 → 버전 → 문항 구성 → 답변 → 채점 → 동의 잠금 → 저장.
     * 입력 오류를 먼저 걸러낸 뒤 저장 직전에만 동의 행을 잠가, 검증에 걸리는 시간만큼 잠금을 쥐고
     * 있지 않게 한다.
     */
    @Transactional
    public StressTestResultResponse submit(Integer studentId, StressTestSubmitRequest request) {
        ensureActiveStudent(studentId);
        if (!TEST_VERSION.equals(request.testVersion())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<PsychologicalTestQuestion> questions = getValidatedActiveQuestions();
        int totalScore = scoreAnswers(questions, request.answers());
        ScoreJudgment judgment = judgeScore(totalScore);

        Instant now = Instant.now();
        UserConsent consentCandidate = consentVerifier.findCurrentValidConsent(
                        studentId, ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED));
        // requireOwnedValidConsent는 실패 사유를 구분하지 않고 항상 FORBIDDEN(A004)을 던진다(공통 정책).
        // 이 기능의 계약은 U009이므로 여기서만 변환하고, FORBIDDEN이 아닌 다른 예외(저장소 장애 등)는
        // 동의 없음으로 삼키지 않도록 그대로 다시 던진다.
        try {
            consentVerifier.requireOwnedValidConsent(
                    consentCandidate.getUserConsentId(),
                    studentId,
                    ConsentModuleCode.COUNSELING,
                    ConsentType.PERSONAL_INFO,
                    now
            );
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
                throw new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
            }
            throw e;
        }

        // 존재가 이미 isActiveStudent로 확인된 학생이므로 추가 조회 없이 지연 참조만 만들어 FK로 연결한다.
        AppUser student = appUserRepository.getReferenceById(studentId);
        PsychologicalTestResult result = PsychologicalTestResult.createSelfTestResult(
                student,
                TEST_TYPE,
                TEST_VERSION,
                totalScore,
                judgment.resultLevel(),
                judgment.resultDescription(),
                now
        );
        return StressTestResultResponse.from(psychologicalTestResultRepository.save(result));
    }

    /** 이력 조회는 철회된 동의로도 가능하므로 여기서는 동의를 다시 확인하지 않는다. */
    public PageResponse<StressTestResultResponse> getHistory(Integer studentId, int page, int size) {
        ensureActiveStudent(studentId);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "testedAt")
                        .and(Sort.by(Sort.Direction.DESC, "psychologicalTestResultId"))
        );
        return PageResponse.from(psychologicalTestResultRepository
                .findByStudentUserIdAndTestType(studentId, TEST_TYPE, pageRequest)
                .map(StressTestResultResponse::from));
    }

    private void ensureActiveStudent(Integer studentId) {
        if (!counselUserRepository.isActiveStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 문항 수, 번호 연속성, 선택지 구성 중 하나라도 확정값과 다르면 실패 원인을 구분해 알리지 않고
     * 전부 S014 하나로 통합한다. 어떤 조건이 깨졌는지 노출하면 문항 데이터 구조를 추측하는 데
     * 악용될 수 있고, 어차피 학생이 스스로 고칠 수 있는 문제가 아니라 서버 설정 문제이기 때문이다.
     */
    private List<PsychologicalTestQuestion> getValidatedActiveQuestions() {
        List<PsychologicalTestQuestion> questions = psychologicalTestQuestionRepository
                .findByTestTypeAndTestVersionOrderByQuestionNoAsc(TEST_TYPE, TEST_VERSION);
        if (questions.size() != QUESTION_COUNT) {
            throw new BusinessException(ErrorCode.STRESS_TEST_NOT_AVAILABLE);
        }
        for (int i = 0; i < questions.size(); i++) {
            PsychologicalTestQuestion question = questions.get(i);
            if (question.getQuestionNo() == null || question.getQuestionNo() != i + 1) {
                throw new BusinessException(ErrorCode.STRESS_TEST_NOT_AVAILABLE);
            }
            validateOptions(question.getResponseOptions());
        }
        return questions;
    }

    /**
     * AssessmentQuestionResponse.ResponseOption과 같은 이유로, 응답 DTO에는 Jackson 2 JsonNode를
     * 그대로 담지 않고 평범한 record 리스트로 변환한다(Spring Boot 4의 Jackson 3 HTTP 직렬화가
     * Jackson 2 JsonNode를 트리로 인식하지 못하고 빈(bean)으로 오인하는 것을 막기 위해서다).
     */
    private List<StressTestQuestionsResponse.Option> toOptionList(JsonNode options) {
        List<StressTestQuestionsResponse.Option> result = new java.util.ArrayList<>();
        if (options != null) {
            options.forEach(node ->
                    result.add(new StressTestQuestionsResponse.Option(node.get("value").asInt(), node.get("label").asText())));
        }
        return result;
    }

    private void validateOptions(JsonNode options) {
        if (options == null || !options.isArray() || options.size() != CANONICAL_OPTIONS.size()) {
            throw new BusinessException(ErrorCode.STRESS_TEST_NOT_AVAILABLE);
        }
        for (int i = 0; i < CANONICAL_OPTIONS.size(); i++) {
            CanonicalOption expected = CANONICAL_OPTIONS.get(i);
            JsonNode option = options.get(i);
            boolean matches = option != null
                    && option.hasNonNull("value") && option.get("value").asInt() == expected.value()
                    && option.hasNonNull("label") && expected.label().equals(option.get("label").asText());
            if (!matches) {
                throw new BusinessException(ErrorCode.STRESS_TEST_NOT_AVAILABLE);
            }
        }
    }

    /**
     * 요청 답변 집합이 현재 문항 ID 집합과 정확히 같은지(중복·누락·다른 문항 없음) 확인하고
     * 서버가 직접 합계를 계산한다. 문항별 선택값 자체는 이 메서드 실행 중에만 스택에 존재하다
     * 합계로 축약된 뒤 사라지며, 어디에도 별도로 저장하지 않는다.
     */
    private int scoreAnswers(List<PsychologicalTestQuestion> questions, List<StressTestSubmitRequest.Answer> answers) {
        Set<Long> currentQuestionIds = questions.stream()
                .map(q -> q.getPsychologicalTestQuestionId().longValue())
                .collect(Collectors.toSet());
        Set<Long> answeredQuestionIds = answers.stream()
                .map(StressTestSubmitRequest.Answer::questionId)
                .collect(Collectors.toSet());
        if (answeredQuestionIds.size() != answers.size() || !answeredQuestionIds.equals(currentQuestionIds)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        int sum = 0;
        for (StressTestSubmitRequest.Answer answer : answers) {
            sum += answer.selectedValue();
        }
        return sum;
    }

    /** 설계 4.2의 5개 점수 구간을 작은 if 연쇄로만 판정한다(외부 enum·전략 클래스 없음). */
    private ScoreJudgment judgeScore(int totalScore) {
        if (totalScore <= 11) {
            return new ScoreJudgment("매우 낮음", "거의 스트레스를 받지 않는 상태, 지금 그대로 잘 유지한다.");
        }
        if (totalScore <= 13) {
            return new ScoreJudgment("낮음",
                    "약간 스트레스를 받고 있으니 사람과의 교제를 늘리고, 내게 주어진 일을 억지로 맡는 것이 아니라 "
                            + "자신을 위해 한다고 생각하고 임해본다.");
        }
        if (totalScore <= 16) {
            return new ScoreJudgment("평균",
                    "비교적 스트레스가 심한 편이므로 스트레스의 원인을 찾아서 적극적으로 맞서보자. "
                            + "적절한 운동과 고른 영양 섭취, 충분한 수면이 필요하다.");
        }
        if (totalScore <= 20) {
            return new ScoreJudgment("높음",
                    "최악은 아니지만 심한 스트레스를 받고 있으므로 우선 신체 상태에 대한 정기적인 검진을 하고, "
                            + "스트레스의 원인을 찾아 줄이기 위한 적극적 대책이 필요하다.");
        }
        return new ScoreJudgment("매우 높음",
                "탈진기라 부르는 위험기이다. 신체의 저항력은 떨어지고 피로가 축적되어 탈진기로 넘어가게 된다. "
                        + "이때는 스트레스에 대한 몸의 방어능력을 잃게 되어 각종 신체 질병이나 정신질환이 나타날 수 있으니 "
                        + "두려워 말고 정신과 상담을 받아본다.");
    }

    private record CanonicalOption(int value, String label) {
    }

    private record ScoreJudgment(String resultLevel, String resultDescription) {
    }
}
