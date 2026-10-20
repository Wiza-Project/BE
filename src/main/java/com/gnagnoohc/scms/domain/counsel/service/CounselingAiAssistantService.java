package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiChatRequest;
import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiChatRequest.Draft;
import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiChatRequest.Filters;
import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiChatResponse;
import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiChatResponse.Action;
import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiIntent;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingScheduleAvailabilityResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingTypeResponse;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 상담 전 대화를 LLM으로 해석하고, DB의 상담 유형·예약 가능 일정과 결합해 신청 초안을 만든다.
 *
 * <p>이 서비스는 DB에 쓰지 않는다. 예약 서비스도 호출하지 않는다. 모델은 "의도"만 제안하고,
 * 일정·정원·마감 판정은 기존 서비스 결과와 예약 POST가 담당한다.</p>
 *
 * <p>클래스에 {@code @Transactional}을 두지 않은 이유: 외부 모델 호출은 수 초가 걸릴 수 있는데,
 * 트랜잭션이 열려 있으면 그 시간 동안 DB 커넥션을 붙잡는다. 유형·일정 조회는 각 기존 서비스의
 * 짧은 readOnly 트랜잭션 안에서 끝나므로 여기서는 트랜잭션이 필요 없다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CounselingAiAssistantService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_CONTENT_LENGTH = 3_000;
    private static final int MAX_SCHEDULE_RECOMMENDATIONS = 5;
    private static final String DIRECT_ROUTE = "DIRECT";
    private static final Set<String> MODEL_ACTIONS =
            Set.of("INFORM", "UPDATE_DRAFT", "ASK_CONFIRMATION", "CONFIRM_RESERVATION");

    // 과거형 완료 단정 탐지(예: "신청되었습니다", "예약이 완료", "접수됐어요", "확정되었습니다").
    // "신청할까요?", "접수 전에 확인할게요"는 걸리지 않는다.
    private static final Pattern COMPLETION_CLAIM =
            Pattern.compile("(신청|예약|접수|확정)\\s*(이|가|을|를)?\\s*(되었|됐|완료)");

    // 신청 내용이 실제로 바뀌지 않았는데 "반영했습니다" 같은 변경 완료 단정을 하는 문장 탐지(E3).
    // "반영할게요"·"정리해 드릴까요?"처럼 과거형이 아닌 문장은 걸리지 않는다.
    private static final Pattern CONTENT_CHANGE_CLAIM =
            Pattern.compile("(반영|정리|요약|저장|기록|작성)\\s*(했|됐|되었|완료)");

    // 학생이 번호·순서·날짜·시간으로 일정을 지목했는지 탐지(E1).
    // "일정"이라는 단어 자체는 지목으로 보지 않는다("그 일정으로 신청할게요"는 현재 초안 일정을 뜻하기 쉽다).
    // FE 카드 버튼 말풍선 "N번 일정(M월 D일 HH:mm)으로 할게요."는 "N번"이 있어 지목으로 판정된다.
    private static final Pattern SCHEDULE_REFERENCE = Pattern.compile(
            "\\d+\\s*(번|월|일|시)|번째|(첫|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*번|마지막|요일|오늘|내일|모레|글피"
                    + "|(이번|다음|다다음)\\s*주|오전|오후|아침|점심|저녁|밤");

    // 자연어 승인 방어용 목록(허용 + 차단). 차단어가 하나라도 있으면 확정하지 않는다.
    // "괜찮아요"·"됐어요"는 승인·거절 둘 다로 쓰여 확정하지 않는 쪽(차단)에 둔다.
    // "아니 좋아요"처럼 차단어가 섞인 승인은 확정되지 않지만, 확인 질문이 한 번 더 나올 뿐이고 버튼 승인은 항상 가능하다.
    // 공백 변형("하지 마"/"하지마")은 \s*로 처리한다.
    // 부정 어미도 차단한다: "신청하지 않을게요"는 허용어 "신청"·"할게"를 포함해 "않"이 없으면 승인으로 잘못 판정됐다.
    // "없"은 "문제 없어요"처럼 승인으로도 쓰이지만 "필요 없어요" 같은 거절과 구분할 수 없어 확정하지 않는 쪽에 둔다.
    private static final Pattern AGREEMENT_BLOCK = Pattern.compile(
            "아니|아뇨|싫|말고|취소|잠깐|잠시만|나중|하지\\s*마|안\\s*할|안\\s*해|안\\s*하|않|못|없"
                    + "|말래|말게|말자|말아|그만|근데|그런데|하지만|대신"
                    + "|바꾸|바꿔|변경|수정|고쳐|보류|생각해|글쎄|별로|됐어|됐습니다|괜찮"
                    // 질문형도 승인이 아니다: "신청 방법을 알려 주세요", "신청은 언제 되나요" 등.
                    + "|알려|어떻게|언제|얼마나|방법|설명|궁금|무엇|뭐|왜|어디|몇|나요|까요");
    // 한 글자 승인어(응·네·예 등)와 "그래"는 다른 단어의 일부로도 흔히 나온다("예전 일정이 나아요"의 "예", "그래서").
    // 그래서 문장 처음이나 공백·쉼표 뒤에 단독으로 올 때만 승인으로 본다(뒤에 "요"가 붙는 "네요"·"그래요"는 허용).
    // "신청"·"진행"·"부탁"은 단어만으로는 질문("신청 방법", "진행 상황")에도 나오므로, 완성된 승인 발화 형태
    // ("신청할게요", "신청해 주세요", "진행해 주세요", "부탁드려요")일 때만 인정한다. 단독 "해 주세요"는 "설명해 주세요"에도 걸려 뺐다.
    private static final Pattern AGREEMENT_ALLOW = Pattern.compile(
            "(^|[\\s,])(응|웅|네|넵|예|옙)(요)?($|[\\s,])|(^|[\\s,])그래(요)?($|[\\s,])|그렇게\\s*(해|할|하)"
                    + "|좋아|좋습니다|좋네요|오케이|ok|okay|맞아|맞습니다"
                    + "|(신청|진행)\\s*(할게|해\\s*주세요|해줘|하겠|합니다|부탁)|부탁\\s*(해|드|합)|할게");
    private static final int MAX_AGREEMENT_LENGTH = 30;
    private static final String AGREEMENT_GUIDE_REPLY =
            "신청하려면 '네, 신청할게요'라고 말씀하시거나 신청 버튼을 눌러 주세요.";

    private static final String OPEN_GENERAL_REPLY =
            "지금은 AI 안내를 이용할 수 없습니다. 일반 상담 신청 화면에서 신청해 주세요.";
    private static final String SCHEDULE_UNAVAILABLE_REPLY =
            "선택한 일정은 더 이상 신청할 수 없습니다. 다른 일정을 선택해 주세요.";
    private static final String EMPTY_CONTENT_MARK = "(비어 있음)";
    private static final String NO_SCHEDULE_SUFFIX = " 조건에 맞는 예약 가능 일정이 없습니다.";
    private static final String TYPE_REASON =
            "대화 내용을 바탕으로 한 1차 안내이며 의료적 진단이 아닙니다. 상담사가 상담 과정에서 최종적으로 도와드립니다.";

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final CounselingTypeService counselingTypeService;
    private final CounselingScheduleService counselingScheduleService;
    private final CounselUserRepository counselUserRepository;

    public CounselingAiChatResponse chat(Integer studentId, CounselingAiChatRequest request) {
        // 유형·일정이 하나도 없어도 비활성 계정은 항상 여기서 막는다.
        if (!counselUserRepository.isActiveStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // CENTER 경로 유형은 기존 예약 POST가 C001로 거절하므로 AI 후보에서 제외한다.
        // getActiveCounselingTypes()도 현재는 DIRECT만 돌려주지만, 그 메서드는 학생 화면용 유형 목록이라 범위가
        // 바뀔 수 있다. AI의 "DIRECT만 후보" 불변식을 그 조회 조건에 숨겨 의존시키지 않으려고 의도적으로 한 번 더 거른다.
        List<CounselingTypeResponse> types = counselingTypeService.getActiveCounselingTypes().stream()
                .filter(type -> DIRECT_ROUTE.equals(type.applicationRoute()))
                .toList();

        // 조회는 모델 호출 전에 끝낸다. 프롬프트에 후보 정보가 필요하고, 모델 호출을 트랜잭션 밖에 두기 위해서다.
        // 여기서 나는 BusinessException은 정상 오류이므로 잡지 않고 전파한다.
        List<AvailableSchedule> available = new ArrayList<>();
        for (CounselingTypeResponse type : types) {
            counselingScheduleService.getAvailableSchedules(type.counselingTypeId(), studentId)
                    .forEach(schedule -> available.add(new AvailableSchedule(type, schedule)));
        }

        CounselingAiChatRequest.Context context = request.context();
        int basedOnVersion = context == null ? 0 : context.version();
        Draft inputDraft = context == null || context.draft() == null
                ? new Draft(null, null, null) : context.draft();
        // 모든 필드가 null인 조건 객체는 조건 없음(null)으로 맞춘다. 그래야 변경 여부 비교가 정확하다.
        Filters inputFilters = context == null ? null : emptyToNull(context.filters());
        List<CounselingAiChatRequest.Candidate> candidates = context == null || context.candidates() == null
                ? List.of() : context.candidates();
        boolean awaiting = context != null && context.awaitingConfirmation();

        CounselingAiIntent intent = callModel(request.message(), types, available, inputDraft, inputFilters,
                candidates, awaiting);

        // OPEN_GENERAL_APPLICATION은 서버만 결정한다. 모델이 이 값을 내게 두면 모델 출력이 흐름 제어를
        // 좌우하게 되므로, 허용 4종이 아니거나 검증을 통과하지 못한 출력은 모두 "해석 실패"로 본다.
        if (!isValidIntent(intent, types, candidates)) {
            return new CounselingAiChatResponse(OPEN_GENERAL_REPLY, basedOnVersion, Action.OPEN_GENERAL_APPLICATION,
                    context == null ? null : context.draft(), inputFilters, null, List.of());
        }

        // ---- 초안 계산 ----
        Integer typeId = inputDraft.counselingTypeId();
        Integer scheduleId = inputDraft.scheduleId();
        String content = inputDraft.requestContent();
        if (intent.counselingTypeId() != null) {
            if (!intent.counselingTypeId().equals(typeId)) {
                scheduleId = null; // 다른 유형으로 바꾸면 이전 유형의 일정은 의미가 없다.
            }
            typeId = intent.counselingTypeId();
        }
        // 입력 초안에 일정이 이미 있는데 학생이 번호·순서·날짜·시간으로 일정을 지목하지 않았으면, 모델이 채운
        // 후보 번호로 일정을 바꾸지 않는다. 모델 지시만으로는 막지 못해 "신청할게요" 한마디에 다른 일정으로
        // 확인 카드가 뜨고 예약까지 된 사례가 있었다(E1). 번호 없이 바꾸려는 발화("다른 거로 할래요")는
        // 일정이 유지되고 모델이 되묻게 된다(안전한 쪽). 범위 검증은 위 isValidIntent에서 이미 끝났다.
        if (intent.selectedCandidateNumber() != null
                && (inputDraft.scheduleId() == null || mentionsSchedule(request.message()))) {
            // 모델은 학생이 말한 번호(1부터)를 준다. 0 기반 목록 접근은 서버가 변환한다(범위는 isValidIntent가 이미 검증).
            CounselingAiChatRequest.Candidate picked = candidates.get(intent.selectedCandidateNumber() - 1);
            typeId = picked.counselingTypeId();
            scheduleId = picked.scheduleId();
        }
        // 모델은 "조건 유지"를 null 대신 모든 필드가 null인 객체로 보내기도 한다. 빈 객체를 "해제"로 보면
        // "응" 한마디에 기존 조건이 지워지고 신청 확인이 막히므로, 빈 객체는 유지로 보고 해제는 clearFilters로만 받는다.
        Filters modelFilters = emptyToNull(intent.filters());
        Filters filters = Boolean.TRUE.equals(intent.clearFilters()) ? null
                : modelFilters != null ? modelFilters : inputFilters;
        boolean filtersChangedThisTurn = !Objects.equals(filters, inputFilters);

        // 신청 내용 누적 요약 병합(R1). 빈 값 정규화보다 "삭제·교체·유지" 의도 판정을 먼저 한다.
        // 모델이 빈 값을 줬다고 지워 버리면, 새 정보가 없는 턴(일정 선택, "응")마다 이전 요약이 사라진다.
        if (Boolean.TRUE.equals(intent.clearRequestContent())) {
            content = null; // 학생이 명시적으로 전체 삭제를 요청한 경우만
        } else if (hasText(intent.requestContent())
                // 프롬프트의 빈 요약 표시를 모델이 그대로 복사해 돌려주면 신청 내용으로 채택하지 않는다.
                && !EMPTY_CONTENT_MARK.equals(intent.requestContent().trim())) {
            String modelContent = intent.requestContent().trim();
            // 일정 조건을 바꾼 턴(Q13): 실측에서 모델이 "다음 주로 바꿀래요"를 신청 내용으로 오해해 기존 고민을 덮어썼다.
            // 그래서 이런 턴에는 이전 요약을 그대로 포함한(뒤에 이어 붙인) 새 요약만 받는다. 이어 붙이면
            // "다음 주로 바꾸고 이력서 얘기도 추가해 줘"처럼 한 문장에 섞인 내용 추가도 반영된다.
            // ponytail: 모델이 이전 문장을 바꿔 쓰면 이번 턴의 추가분은 빠진다(이전 요약은 보존). 확인 카드에 보이며 다음 턴에 다시 말하면 반영된다.
            if (!filtersChangedThisTurn || content == null || modelContent.contains(content)) {
                content = modelContent; // 모델이 돌려준 "이전 요약 + 이번 내용" 전체로 교체
            }
        } // 그 외(null·빈 값·공백)는 이전 요약 유지
        String reply = hasText(intent.reply()) ? intent.reply().trim() : null;

        // 클라이언트가 보낸 후보·초안에 AI가 다룰 수 없는 유형(CENTER, 비활성 등)이 있으면 비운다.
        // 남겨 두면 그 유형으로만 일정을 걸러 추천이 계속 0건이 된다.
        Integer finalTypeId = typeId;
        if (finalTypeId != null && types.stream().noneMatch(t -> t.counselingTypeId().equals(finalTypeId))) {
            typeId = null;
        }

        // 학생이 이번 턴에 일정 조건을 바꿨는데 이미 고른 일정이 새 조건에 맞지 않으면 선택을 해제한다(Q12=A).
        // 학생은 다른 날짜·시간을 원한다는 뜻이므로, 기존 일정을 남겨 두면 바꾸려던 일정으로 확인 카드가 뜬다.
        // 새 조건에 맞는 일정이면 그대로 둔다. 가용 목록에 없는 일정은 아래 D14 분기가 처리한다.
        Integer selectedId = scheduleId;
        if (selectedId != null && !Objects.equals(filters, inputFilters)
                && available.stream().anyMatch(a -> a.schedule().scheduleId().equals(selectedId)
                        && !matches(a.schedule(), filters))) {
            scheduleId = null;
        }

        Draft draft = new Draft(typeId, scheduleId, content);
        Action action;

        // 가용성 대조: 후보를 표시한 뒤 마감·정원 소진으로 일정이 사라졌을 수 있다(정상적인 데이터 변화).
        // 이 대조는 안내용일 뿐 최종 검증이 아니다. 예약 POST가 정원·마감·중복을 다시 검증한다.
        if (scheduleId != null && !isAvailable(available, scheduleId, typeId)) {
            draft = new Draft(typeId, null, content);
            action = Action.UPDATE_DRAFT;
            reply = SCHEDULE_UNAVAILABLE_REPLY;
        } else {
            action = decideAction(intent.action(), awaiting, draft, inputDraft, filters, inputFilters);
            // 모델 분류만 믿으면 부정 발화("싫어요")가 확정될 수 있어(실측 2/3), 서버가 학생 발화 자체를 한 번 더 확인한다.
            // 확정이 틀리면 원치 않는 예약이 생기므로 모르는 표현은 확정하지 않고 확인 질문으로 되돌린다.
            boolean agreementRejected = action == Action.CONFIRM_RESERVATION && !isExplicitAgreement(request.message());
            if (agreementRejected) {
                action = Action.ASK_CONFIRMATION;
            }
            // 확인 카드가 떠 있을 때 카드와 같은 일정을 지목한 답("좋아", "첫 번째 일정으로 할게요")은 카드에 대한
            // 승인으로 본다(Q15=A). 다른 일정을 고르면 초안이 바뀌므로 decideAction이 이미 CONFIRM을 막는다.
            // 이전 D18 규칙은 후보가 1건일 때 모델이 수긍에도 후보를 채워 자연어 승인이 불가능해지는 문제가 있었다.
            reply = decideReply(action, intent.action(), reply, draft,
                    !Objects.equals(draft.requestContent(), inputDraft.requestContent()));
            if (agreementRejected) {
                reply = AGREEMENT_GUIDE_REPLY; // 모델 문장 대신, 어떻게 승인하면 되는지 알려 주는 고정 문구
            }
            // 확정 응답은 학생이 본 초안을 그대로 돌려줘야 하므로 입력 draft를 유지한다.
            if (action == Action.CONFIRM_RESERVATION) {
                draft = inputDraft;
            }
        }

        List<CounselingAiChatResponse.ScheduleRecommendation> recommendations = recommend(
                available, draft.counselingTypeId(), filters, intent.recommendedCounselingTypeId());
        // 안내·갱신 응답(INFORM·UPDATE_DRAFT)에만 붙인다. 완성 초안에서 조건을 바꿔 다시 찾다가 0건이 된 경우도
        // 알려야 하고, 확인·확정 응답의 고정 문구(특히 CONFIRM_RESERVATION)는 정확히 유지해야 한다.
        if (recommendations.isEmpty() && (action == Action.INFORM || action == Action.UPDATE_DRAFT)) {
            // 모델이 없는 일정을 말했을 가능성에 대비해 서버가 사실을 덧붙인다.
            reply = reply + NO_SCHEDULE_SUFFIX;
        }

        Integer recommendedId = intent.recommendedCounselingTypeId() != null
                ? intent.recommendedCounselingTypeId() : draft.counselingTypeId();
        CounselingAiChatResponse.CounselingTypeRecommendation typeRecommendation = types.stream()
                .filter(type -> type.counselingTypeId().equals(recommendedId))
                .findFirst()
                .map(type -> new CounselingAiChatResponse.CounselingTypeRecommendation(
                        type.counselingTypeId(), type.typeCode(), type.typeName(), TYPE_REASON))
                .orElse(null);

        return new CounselingAiChatResponse(reply, basedOnVersion, action, draft, filters,
                typeRecommendation, recommendations);
    }

    /** 모델을 한 번만 호출한다. 실패·미설정이면 null을 돌려주고, 호출자는 이를 해석 실패로 처리한다. */
    private CounselingAiIntent callModel(
            String message,
            List<CounselingTypeResponse> types,
            List<AvailableSchedule> available,
            Draft draft,
            Filters filters,
            List<CounselingAiChatRequest.Candidate> candidates,
            boolean awaiting
    ) {
        try {
            ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
            if (builder == null) {
                return null;
            }
            return builder.build().prompt()
                    .system(buildSystemPrompt(types, available, draft, filters, candidates, awaiting))
                    .user(message)
                    .call()
                    .entity(CounselingAiIntent.class);
        } catch (Exception e) {
            // 예외 객체·메시지는 남기지 않는다. 예외 메시지에 사용자 원문이나 모델 응답이 섞일 수 있고,
            // 상담 내용은 민감정보라 로그로 새면 안 된다.
            log.warn("상담 AI 모델 호출 실패: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String buildSystemPrompt(
            List<CounselingTypeResponse> types,
            List<AvailableSchedule> available,
            Draft draft,
            Filters filters,
            List<CounselingAiChatRequest.Candidate> candidates,
            boolean awaiting
    ) {
        ZonedDateTime now = ZonedDateTime.now(KST);
        String typeText = types.stream()
                .map(t -> t.counselingTypeId() + "=" + t.typeCode() + "=" + t.typeName())
                .collect(Collectors.joining(", "));
        // 상담사 이름 등 불필요한 개인정보는 외부 모델로 보내지 않고 번호(1부터)·유형명·시작 시각만 전달한다.
        StringBuilder candidateText = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            CounselingAiChatRequest.Candidate c = candidates.get(i);
            String typeName = types.stream().filter(t -> t.counselingTypeId().equals(c.counselingTypeId()))
                    .map(CounselingTypeResponse::typeName).findFirst().orElse("알 수 없음");
            String startText = available.stream()
                    .filter(a -> a.schedule().scheduleId().equals(c.scheduleId()))
                    .map(a -> a.schedule().startsAt().atZone(KST).toLocalDateTime().toString())
                    .findFirst().orElse("신청 불가");
            candidateText.append(i + 1).append("번: ").append(typeName).append(", ").append(startText).append('\n');
        }
        String content = draft.requestContent() == null ? EMPTY_CONTENT_MARK : draft.requestContent();
        // 프롬프트 설계 이유(R1):
        // - "새 정보가 없으면 requestContent를 null로": 모델이 이전 요약을 다시 쓰면 문자열이 조금이라도 달라져
        //   서버가 "초안 내용이 바뀜"으로 판정하고, 확정 대신 신청 확인 질문이 반복된다.
        // - 요약 500자 권장: 확인 카드·상담사 목록에서 읽기 쉬운 분량이고, 누적돼도 서버 상한 3,000자까지 여유가 있다.
        //   (500자는 모델에게 주는 지침일 뿐이며 서버 검증 상한은 MAX_CONTENT_LENGTH 그대로다.)
        return """
                당신은 대학생의 상담 신청을 돕는 안내 챗봇의 의도 분석기다.
                오늘 날짜와 현재 시각(Asia/Seoul): %s
                선택 가능한 상담 유형(id=code=name): %s
                현재 신청 초안: counselingTypeId=%s, scheduleId=%s, requestContent=%s
                현재 일정 조건(filters): %s
                직전 확인 질문 대기 중(awaitingConfirmation): %s
                직전에 표시한 후보(번호: 유형, 시작 시각):
                %s
                규칙:
                - 의료적 진단이나 위험도 판단을 하지 말라.
                - 일정·상담사를 새로 만들지 말라. 일정은 후보 번호(selectedCandidateNumber)로만 고른다.
                - selectedCandidateNumber는 학생이 이번 발화에서 일정을 직접 골랐을 때만 채워라. 유형만 고르거나 일정 언급이 없으면 null로 하고, 임의로 고르지 말라. 값은 학생이 말한 번호 그대로(1부터)다. '두 번째'는 2, '마지막 거'는 마지막 번호다. awaitingConfirmation=true에서 현재 초안과 같은 일정을 지목하는 답도 수긍이다. 다른 일정을 고르면 CONFIRM_RESERVATION을 쓰지 말라.
                - 모호한 날짜(다음 주 등)나 종료 시각 조건처럼 filters로 표현할 수 없는 조건은 filters를 바꾸지 말고 action=INFORM으로 되물어라.
                - 가장 먼저 확인할 규칙: awaitingConfirmation이 true이고 학생이 변경 요청 없이 수긍만 하면("응", "네", "좋아요", "그걸로 신청할게요" 등) 반드시 action=CONFIRM_RESERVATION, requestContent=null로 하라.
                - 수긍 없이 이미 고른 일정·날짜·시간을 바꿔 달라고 요청할 때만(예: "다음 주로 바꾸고 싶어요") 신청 확인이 아니다. 이때는 ASK_CONFIRMATION·CONFIRM_RESERVATION을 쓰지 말고, 날짜가 모호하면 INFORM으로 원하는 날짜를 되묻고, 구체적이면 filters를 바꿔 UPDATE_DRAFT로 하라.
                - 일정·날짜·시간에 관한 말은 신청 내용이 아니다. requestContent에 넣지 말라.
                - 한 발화에 일정 변경과 고민·요청 추가가 함께 있으면(예: "다음 주로 바꾸고, 이력서 얘기도 추가해 주세요") 둘 다 처리하라. 일정 변경을 빠뜨리지 말고 filters를 바꾸거나(날짜가 모호하면 INFORM으로 되묻기) 함께, requestContent는 이전 요약 문장을 한 글자도 고치지 말고 그 뒤에 새 내용만 이어 붙인 전체로 반환하라.
                - counselingTypeId는 학생이 명시적으로 선택한 유형일 때만 채우고, 단순 추천은 recommendedCounselingTypeId에 넣어라.
                - requestContent는 신청 내용의 누적 요약이다. 이번 발화에 새 고민·요청·수정이 있으면 "현재 신청 초안의 requestContent"(이전 요약)에 이번 내용을 합친 전체 요약을 반환하라. 학생이 일부 수정·교체를 요청하면 반영한 전체 요약을 반환하라.
                - 유형을 요청하는 문장에 이유·고민이 함께 있으면(예: "졸업 후 진로가 걱정돼서 진로상담을 받고 싶어요") counselingTypeId 선택과 함께 그 고민을 requestContent로 요약하라. 유형만 채우고 고민을 버리지 말라.
                - 이번 발화에 신청 내용과 관련된 새 정보가 없으면(예: 일정 선택, 짧은 수긍, 날짜 질문) requestContent는 null로 하라. 이전 요약을 다시 쓰지 말라.
                - 요약에는 학생이 말한 사실만 쓰고, 진단·추정·학생이 말하지 않은 사실을 보태지 말라. 요약은 500자 이내로 간결하게 쓰라.
                - 학생이 신청 내용 전체 삭제를 요청하면 clearRequestContent=true로 하라. 그 외에는 null로 하라.
                - reply에서 예약이 완료·확정·접수됐다고 말하지 말라. 이 챗봇은 신청 의사만 확인하며 실제 신청 결과를 알 수 없다.
                - 신청 초안이 미완성이면 reply 끝에 상담 유형, 일정, 신청 내용 순서로 첫 번째 빈 항목을 질문하라. 단, 날짜·시간이 모호하면 그 재질문을 우선하라.
                - filters는 바꿀 때 현재 조건 전체를 담고(날짜 yyyy-MM-dd, 시각 HH:mm), 유지하려면 null로 하라.
                - 학생이 날짜·시간 조건을 없애 달라고 하면(예: 아무 날이나 괜찮아) clearFilters=true로 하라. 그 외에는 null로 하라.
                - 짧은 수긍은 awaitingConfirmation이 true일 때만 action=CONFIRM_RESERVATION으로 하라. 부정·조건부·수정 요청에는 CONFIRM_RESERVATION을 쓰지 말라.
                - action은 INFORM, UPDATE_DRAFT, ASK_CONFIRMATION, CONFIRM_RESERVATION 중 하나다. 유형·일정·신청 내용을 반영했고 되물을 것이 없으면 UPDATE_DRAFT, 학생에게 되묻거나 질문에 답만 할 때는 INFORM을 쓴다.
                - reply는 한국어의 짧은 안내 문장이다.
                JSON만 반환하라.
                """.formatted(
                // 요일이 없으면 "이번 주 금요일" 같은 상대 날짜를 모델이 잘못 계산하기 쉽다.
                now.toLocalDate() + "(" + now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN) + ") "
                        + now.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                typeText.isEmpty() ? "없음" : typeText,
                draft.counselingTypeId(), draft.scheduleId(), content,
                filters == null ? "없음" : filters,
                awaiting,
                candidateText.length() == 0 ? "없음" : candidateText.toString());
    }

    /** 모델 출력이 서버가 허용한 범위 안인지 검사한다. 하나라도 어긋나면 해석 실패다. */
    private boolean isValidIntent(
            CounselingAiIntent intent,
            List<CounselingTypeResponse> types,
            List<CounselingAiChatRequest.Candidate> candidates
    ) {
        if (intent == null || intent.action() == null || !MODEL_ACTIONS.contains(intent.action())) {
            return false;
        }
        Integer number = intent.selectedCandidateNumber();
        if (number != null && (number < 1 || number > candidates.size())) {
            return false;
        }
        Set<Integer> typeIds = types.stream().map(CounselingTypeResponse::counselingTypeId)
                .collect(Collectors.toSet());
        if (intent.counselingTypeId() != null && !typeIds.contains(intent.counselingTypeId())) {
            return false;
        }
        if (intent.recommendedCounselingTypeId() != null
                && !typeIds.contains(intent.recommendedCounselingTypeId())) {
            return false;
        }
        if (intent.requestContent() != null && intent.requestContent().trim().length() > MAX_CONTENT_LENGTH) {
            return false;
        }
        Filters f = intent.filters();
        if (f != null) {
            if (f.dateFrom() != null && f.dateTo() != null && f.dateFrom().isAfter(f.dateTo())) {
                return false;
            }
            return f.startTimeFrom() == null || f.startTimeTo() == null
                    || !f.startTimeFrom().isAfter(f.startTimeTo());
        }
        return true;
    }

    private boolean isAvailable(List<AvailableSchedule> available, Integer scheduleId, Integer typeId) {
        return available.stream().anyMatch(a ->
                a.schedule().scheduleId().equals(scheduleId) && a.type().counselingTypeId().equals(typeId));
    }

    /**
     * 최종 action 결정. 확정(CONFIRM_RESERVATION)은 직전 확인 질문이 있었고, 초안이 완성돼 있고,
     * 이번 턴에 초안·조건이 바뀌지 않았을 때만 허용한다. 그렇지 않은 "응"은 확인 재질문이 된다.
     */
    Action decideAction(String modelAction, boolean awaiting, Draft draft, Draft inputDraft,
                        Filters filters, Filters inputFilters) {
        boolean draftChanged = !Objects.equals(draft, inputDraft);
        boolean filtersChanged = !Objects.equals(filters, inputFilters);
        boolean complete = draft.counselingTypeId() != null && draft.scheduleId() != null
                && draft.requestContent() != null && !draft.requestContent().isEmpty()
                && draft.requestContent().length() <= MAX_CONTENT_LENGTH;
        boolean confirmWord = "CONFIRM_RESERVATION".equals(modelAction);
        if (confirmWord && awaiting && complete && !draftChanged && !filtersChanged) {
            return Action.CONFIRM_RESERVATION;
        }
        // 모델이 INFORM이면 되묻는 중(예: "다음 주"처럼 모호한 날짜 변경)이라, 초안이 완성돼 있어도 확인 카드를 띄우지 않는다.
        // 띄우면 학생이 바꾸려던 기존 일정으로 승인할 위험이 있다(Q11=A). 요약 변경은 UPDATE_DRAFT로 반영하고 다음 턴에 확인받는다.
        boolean modelAsking = "INFORM".equals(modelAction);
        if (complete && !modelAsking && (draftChanged || confirmWord || "ASK_CONFIRMATION".equals(modelAction))) {
            return Action.ASK_CONFIRMATION;
        }
        return draftChanged ? Action.UPDATE_DRAFT : Action.INFORM;
    }

    /**
     * 학생 발화가 "명시적 승인"인지 판정한다. 승인 표현이 있고 부정·보류 표현이 없을 때만 true.
     * 전제: 짧은 수긍만 인정한다(정규화 후 30자 초과면 다른 요청이 섞였을 가능성이 커서 false).
     * 실패 가능성: 허용 목록에 없는 표현("그걸로 가죠")은 false가 되어 확인 질문이 한 번 더 나온다(안전한 쪽).
     */
    boolean isExplicitAgreement(String message) {
        // 물음표가 있으면 질문이지 승인이 아니다. 아래 정규화가 끝의 "?"를 지우므로 그 전에 원문으로 판정한다.
        if (message == null || message.contains("?")) {
            return false;
        }
        // 끝의 문장부호·공백·물결·웃음 자음을 떼고 영문은 소문자로 맞춘다("OK!!" -> "ok").
        String text = message.trim().replaceAll("[.!?~…ㅎㅋ\\s]+$", "").toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.length() > MAX_AGREEMENT_LENGTH) {
            return false;
        }
        return !AGREEMENT_BLOCK.matcher(text).find() && AGREEMENT_ALLOW.matcher(text).find();
    }

    /**
     * 최종 reply 결정.
     * - CONFIRM_RESERVATION은 항상 고정 문구. 이 챗봇은 신청 의사만 확인할 뿐 실제 예약 결과를 모르므로
     *   모델이 "예약이 완료됐어요" 같은 거짓 확정 문구를 만들지 못하게 막는다.
     * - 그 외에는 서버 action과 모델 action이 같을 때만 모델 문장을 쓴다. 서버가 action을 바꿨다면
     *   모델 문장이 실제 동작과 어긋나 사용자를 오도할 수 있으므로 서버 고정 문구로 바꾼다.
     * - 예외: 모델 INFORM + 서버 UPDATE_DRAFT는 모델이 모호한 날짜 재질문과 함께 요약을 갱신한 경우라,
     *   그 질문을 서버 문구로 덮지 않기 위해 모델 문장을 유지한다.
     */
    private String decideReply(Action action, String modelAction, String modelReply, Draft draft,
                               boolean contentChanged) {
        if (action == Action.CONFIRM_RESERVATION) {
            return "확인했어요. 신청을 진행할게요.";
        }
        // 프롬프트로 금지해도 모델이 "일반상담이 신청되었습니다"처럼 어기는 사례가 실제 FE 검증에서 나왔다.
        // chat은 예약을 만들지 않으므로 완료 단정은 항상 거짓이다. 오탐이면 안전한 고정 문구로 바뀔 뿐이다.
        // 모호한 날짜 재질문처럼 완료 표현이 없는 모델 문장은 그대로 보존된다.
        // 실제로는 신청 내용이 바뀌지 않았는데 모델이 "신청 내용을 반영했습니다"라고 말한 사례가 있었다(E3).
        // R2 취지(실제 변경 없이 갱신됐다고 말하지 않음)를 서버에서 보장한다. 내용이 실제로 바뀐 턴의
        // "반영했어요"는 유지된다. 오탐이면 안전한 고정 문구로 바뀔 뿐이다.
        boolean falseChangeClaim = !contentChanged && modelReply != null
                && CONTENT_CHANGE_CLAIM.matcher(modelReply).find();
        boolean keepModel = modelReply != null
                && !COMPLETION_CLAIM.matcher(modelReply).find()
                && !falseChangeClaim
                && (action.name().equals(modelAction)
                || ("INFORM".equals(modelAction) && action == Action.UPDATE_DRAFT));
        if (keepModel) {
            return modelReply;
        }
        return action == Action.ASK_CONFIRMATION
                ? "이 내용으로 상담을 신청할까요?"
                : missingItemQuestion(draft);
    }

    /** 학생 발화가 번호·순서·날짜·시간으로 일정을 지목하는지 판정한다(E1). null이면 false. */
    boolean mentionsSchedule(String message) {
        return message != null && SCHEDULE_REFERENCE.matcher(message).find();
    }

    /** 초안의 첫 빈 항목(상담 유형 → 일정 → 신청 내용 순서)을 묻는 서버 고정 문구. */
    private String missingItemQuestion(Draft draft) {
        if (draft.counselingTypeId() == null) {
            return "어떤 상담 유형으로 신청할까요?";
        }
        if (draft.scheduleId() == null) {
            return "원하는 상담 일정을 골라 주세요. 날짜나 시간대를 말씀해 주셔도 돼요.";
        }
        if (draft.requestContent() == null) {
            return "상담받고 싶은 고민이나 요청 내용을 알려 주세요.";
        }
        return "궁금한 점이 있으면 말씀해 주세요.";
    }

    /** 필터 → 정렬 → limit 순서. limit를 먼저 하면 조건에 맞는 일정이 잘려 나갈 수 있다. */
    List<CounselingAiChatResponse.ScheduleRecommendation> recommend(
            List<AvailableSchedule> available,
            Integer draftTypeId,
            Filters filters,
            Integer recommendedTypeId
    ) {
        return available.stream()
                .filter(a -> draftTypeId == null || a.type().counselingTypeId().equals(draftTypeId))
                .filter(a -> matches(a.schedule(), filters))
                .sorted(Comparator.comparing((AvailableSchedule a) -> a.schedule().startsAt())
                        .thenComparing(a -> a.schedule().scheduleId()))
                .limit(MAX_SCHEDULE_RECOMMENDATIONS)
                .map(a -> {
                    CounselingTypeResponse type = a.type();
                    CounselingScheduleAvailabilityResponse s = a.schedule();
                    String reason = type.counselingTypeId().equals(recommendedTypeId)
                            ? "추천 상담 유형과 일치하는 예약 가능 일정입니다."
                            : "현재 예약 가능한 상담 일정입니다.";
                    return new CounselingAiChatResponse.ScheduleRecommendation(
                            s.scheduleId(), type.counselingTypeId(), type.typeCode(), type.typeName(),
                            s.counselorName(), s.counselorDepartmentName(), s.startsAt(), s.endsAt(),
                            s.bookingDeadline(), s.location(), s.remainingCapacity(), reason);
                })
                .toList();
    }

    private boolean matches(CounselingScheduleAvailabilityResponse schedule, Filters filters) {
        if (filters == null) {
            return true;
        }
        ZonedDateTime start = schedule.startsAt().atZone(KST);
        if (filters.dateFrom() != null && start.toLocalDate().isBefore(filters.dateFrom())) {
            return false;
        }
        if (filters.dateTo() != null && start.toLocalDate().isAfter(filters.dateTo())) {
            return false;
        }
        LocalTime time = start.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        if (filters.startTimeFrom() != null && time.isBefore(filters.startTimeFrom())) {
            return false;
        }
        return filters.startTimeTo() == null || !time.isAfter(filters.startTimeTo());
    }

    record AvailableSchedule(CounselingTypeResponse type, CounselingScheduleAvailabilityResponse schedule) {
    }

    private static Filters emptyToNull(Filters f) {
        return f == null || (f.dateFrom() == null && f.dateTo() == null
                && f.startTimeFrom() == null && f.startTimeTo() == null) ? null : f;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
