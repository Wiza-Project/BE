package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentNonParticipantNotifyRequest;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentNonParticipantNotifyResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentNonParticipantResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentNonParticipantService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 미응시자 목록 조회. 담당 교직원이 미응시자를 확인해야 하는 기능이라 STAFF 전반에게
 * 열어둔다. {@code SecurityConfig}가 {@code /api/staff/**}를 이미 STAFF role로 막고
 * 있고, 여기 {@code @PreAuthorize}는 AdminStudentController와 같은 보조 안전장치일 뿐이다.
 */
@Tag(name = "AssessmentNonParticipant", description = "진단 미응시자 조회")
@RestController
@RequestMapping("/api/staff/assessment-rounds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
public class AssessmentNonParticipantController {

    private final AssessmentNonParticipantService assessmentNonParticipantService;

    @Operation(summary = "미응시자 조회", description = "회차의 응시 대상자 중 아직 제출을 완료하지 않은 학생 명단을 페이지 단위로 조회합니다. "
            + "targetCondition이 없는 회차는 전체 학생을 대상자로 봅니다.")
    @GetMapping("/{roundId}/non-participants")
    public ApiResponse<PageResponse<AssessmentNonParticipantResponse>> getNonParticipants(
            @PathVariable Integer roundId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(assessmentNonParticipantService.getNonParticipants(roundId, pageable));
    }

    @Operation(summary = "미응시자 알림 발송", description = "미응시자에게 마감임박 알림을 발송합니다. "
            + "userIds가 없으면 회차 전체 미응시자가 대상이고, 있으면 실제 미응시자 집합과의 교집합만 대상으로 삼습니다.")
    @PostMapping("/{roundId}/non-participants/notify")
    public ApiResponse<AssessmentNonParticipantNotifyResponse> notify(
            @PathVariable Integer roundId,
            @RequestBody(required = false) AssessmentNonParticipantNotifyRequest request) {
        List<Integer> userIds = request == null ? null : request.userIds();
        return ApiResponse.ok(assessmentNonParticipantService.notify(roundId, userIds));
    }
}
