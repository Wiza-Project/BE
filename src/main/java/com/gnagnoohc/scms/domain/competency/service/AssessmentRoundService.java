package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.competency.dto.request.AssessmentRoundRequest;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentRoundService {

    private static final String DUPLICATE_ROUND_CONSTRAINT = "uq_assessment_round_period_type";

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentQuestionRepository assessmentQuestionRepository;
    private final AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    private final TargetConditionInterpreter targetConditionInterpreter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 교직원 회차 관리 화면 목록. 회차/응시/결과 통계 세 탭이 공유하는 회차 목록의 원천이다.
    @Transactional(readOnly = true)
    public List<AssessmentRoundResponse> getRounds() {
        return assessmentRoundRepository.findAllByOrderByStartsAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AssessmentRoundResponse registerRound(AssessmentRoundRequest request, Integer staffId) {
        validatePeriod(request.startsAt(), request.endsAt());
        validateNoDuplicate(request.academicYear(), request.semesterCode(), request.assessmentType(), null);
        JsonNode targetCondition = mapToJsonNode(request.targetCondition());
        validateTargetConditionShape(targetCondition);

        AssessmentRound round = AssessmentRound.create(
                request.assessmentName(),
                request.academicYear(),
                request.semesterCode(),
                request.assessmentType(),
                request.startsAt(),
                request.endsAt(),
                targetCondition,
                staffId
        );

        try {
            assessmentRoundRepository.save(round);
        } catch (DataIntegrityViolationException e) {
            throw resolveDuplicateRoundViolation(e);
        }

        composeQuestions(round, staffId);
        return toResponse(round);
    }

    /**
     * 회차 개설 시점에 현재 활성 문항 전량을 편성한다(응시 시작 후에는 회차 수정 자체가 막히므로
     * 문항 세트도 이 시점에 확정된다). display_order는 역량 축순서 → 문항ID 순으로 1부터 이어진다.
     * 활성 문항이 하나도 없으면 편성 없이 넘어간다 — 회차 목록/진단 안내에서 "0문항"으로 드러난다.
     */
    private void composeQuestions(AssessmentRound round, Integer staffId) {
        List<AssessmentQuestion> questions = assessmentQuestionRepository.findAllActiveForRoundComposition();
        List<AssessmentRoundQuestion> roundQuestions = new ArrayList<>();
        int displayOrder = 1;
        for (AssessmentQuestion question : questions) {
            roundQuestions.add(AssessmentRoundQuestion.of(round, question, displayOrder++, staffId));
        }
        assessmentRoundQuestionRepository.saveAll(roundQuestions);
    }

    /**
     * 이미 응시(문항 응답)가 시작된 회차는 통째로 수정을 막는다 — 학년도·기간 등 일부만 잠그면
     * 요청 필드별로 변경 여부를 비교하는 복잡한 로직이 필요해지고, 이 저장소의 다른 도메인(P009 등)도
     * "상태가 바뀌면 수정 자체를 차단"하는 방식을 일관되게 쓰고 있다.
     */
    public AssessmentRoundResponse updateRound(Integer roundId, AssessmentRoundRequest request) {
        AssessmentRound round = assessmentRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));

        if (assessmentAttemptRepository.existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(roundId)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_EDITABLE);
        }

        validatePeriod(request.startsAt(), request.endsAt());
        validateNoDuplicate(request.academicYear(), request.semesterCode(), request.assessmentType(), roundId);
        JsonNode targetCondition = mapToJsonNode(request.targetCondition());
        validateTargetConditionShape(targetCondition);

        round.update(
                request.assessmentName(),
                request.academicYear(),
                request.semesterCode(),
                request.assessmentType(),
                request.startsAt(),
                request.endsAt(),
                targetCondition
        );

        try {
            // round.update()는 관리 중인 엔티티를 변경할 뿐이라 saveAndFlush로 강제로 flush해야
            // 유니크 제약 위반이 트랜잭션 커밋 시점이 아니라 이 메서드 안에서 즉시 예외로 드러난다.
            assessmentRoundRepository.saveAndFlush(round);
        } catch (DataIntegrityViolationException e) {
            throw resolveDuplicateRoundViolation(e);
        }

        return toResponse(round);
    }

    // 판정 자체는 TargetConditionInterpreter(hasUnrecognizedKey/isValidShape) 하나로 고정하고
    // (재검토 스레드 참고), 여기서는 등록·수정 요청에 맞는 에러코드(INVALID_INPUT)로만 변환한다.
    private void validateTargetConditionShape(JsonNode targetCondition) {
        if (targetConditionInterpreter.hasUnrecognizedKey(targetCondition)
                || !targetConditionInterpreter.isValidShape(targetCondition)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePeriod(java.time.Instant startsAt, java.time.Instant endsAt) {
        if (!startsAt.isBefore(endsAt)) {
            throw new BusinessException(ErrorCode.INVALID_ASSESSMENT_PERIOD);
        }
    }

    private void validateNoDuplicate(Integer academicYear, String semesterCode, String assessmentType, Integer excludeRoundId) {
        boolean duplicate = excludeRoundId == null
                ? assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentType(
                        academicYear, semesterCode, assessmentType).isPresent()
                : assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentTypeAndAssessmentRoundIdNot(
                        academicYear, semesterCode, assessmentType, excludeRoundId).isPresent();

        if (duplicate) {
            throw new BusinessException(ErrorCode.DUPLICATE_ASSESSMENT_ROUND);
        }
    }

    /**
     * uq_assessment_round_period_type 위반만 중복 회차로 간주한다. assessment_round는 FK가 없지만
     * created_by(Bean Validation이 검증하지 않는 authUser.getId())가 null이면 NOT NULL 위반이 날 수 있는데,
     * 이런 무관한 무결성 위반까지 "중복 회차"로 둔갑시키지 않기 위해 원인 메시지에서 제약명을 직접 확인한다
     * (ProgramService.resolveForeignKeyViolation과 같은 패턴).
     */
    private BusinessException resolveDuplicateRoundViolation(DataIntegrityViolationException e) {
        String detail = e.getMostSpecificCause().getMessage();
        if (detail.contains(DUPLICATE_ROUND_CONSTRAINT)) {
            return new BusinessException(ErrorCode.DUPLICATE_ASSESSMENT_ROUND);
        }
        throw e;
    }

    private JsonNode mapToJsonNode(Map<String, Object> map) {
        return map == null ? null : objectMapper.valueToTree(map);
    }

    private Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        return objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
    }

    private AssessmentRoundResponse toResponse(AssessmentRound round) {
        return new AssessmentRoundResponse(
                round.getAssessmentRoundId(),
                round.getAssessmentName(),
                round.getAcademicYear(),
                round.getSemesterCode(),
                round.getAssessmentType(),
                round.getStartsAt(),
                round.getEndsAt(),
                jsonNodeToMap(round.getTargetCondition()),
                round.getRoundStatus()
        );
    }
}
