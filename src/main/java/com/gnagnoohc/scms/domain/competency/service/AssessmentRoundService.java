package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentRoundRequest;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentRoundService {

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssessmentRoundResponse registerRound(AssessmentRoundRequest request, Integer staffId) {
        validatePeriod(request.startsAt(), request.endsAt());
        validateNoDuplicate(request.academicYear(), request.semesterCode(), request.assessmentType(), null);

        AssessmentRound round = AssessmentRound.create(
                request.assessmentName(),
                request.academicYear(),
                request.semesterCode(),
                request.assessmentType(),
                request.startsAt(),
                request.endsAt(),
                mapToJsonNode(request.targetCondition()),
                staffId
        );

        return toResponse(assessmentRoundRepository.save(round));
    }

    // 이미 응시(문항 응답)가 시작된 회차는 통째로 수정을 막는다 — 학년도·기간 등 일부만 잠그면
    // 요청 필드별로 변경 여부를 비교하는 복잡한 로직이 필요해지고, 이 저장소의 다른 도메인(P009 등)도
    // "상태가 바뀌면 수정 자체를 차단"하는 방식을 일관되게 쓰고 있다.
    public AssessmentRoundResponse updateRound(Integer roundId, AssessmentRoundRequest request) {
        AssessmentRound round = assessmentRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));

        if (assessmentAttemptRepository.existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(roundId)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_EDITABLE);
        }

        validatePeriod(request.startsAt(), request.endsAt());
        validateNoDuplicate(request.academicYear(), request.semesterCode(), request.assessmentType(), roundId);

        round.update(
                request.assessmentName(),
                request.academicYear(),
                request.semesterCode(),
                request.assessmentType(),
                request.startsAt(),
                request.endsAt(),
                mapToJsonNode(request.targetCondition())
        );

        return toResponse(round);
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
