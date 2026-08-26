package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentAttemptResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentIntroResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentIntroService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "StudentAssessmentRound", description = "학생의 핵심역량 진단 안내 조회 및 응시 시작")
@RestController
@RequestMapping("/api/students/assessment-rounds/{roundId}")
@RequiredArgsConstructor
public class StudentAssessmentRoundController {

    private final AssessmentIntroService assessmentIntroService;

    @Operation(summary = "진단 안내 조회", description = "진단명·응시기간·문항수·예상 소요시간을 조회합니다. "
            + "이미 응시를 시작한 적이 있으면 기존 attemptId/attemptStatus를 함께 내려줍니다.")
    @GetMapping("/intro")
    public ApiResponse<AssessmentIntroResponse> getIntro(
            @PathVariable Integer roundId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentIntroService.getIntro(roundId, authUser.getId()));
    }

    @Operation(summary = "응시 시작(동의 확인 후 attempt 생성)", description = "동의(개인정보 처리방침 등) 자체는 /api/consents에서 별도로 처리합니다. "
            + "이 API는 필수 동의를 모두 마쳤는지 확인한 뒤 AssessmentAttempt를 생성해 연결합니다. "
            + "이미 시작한 학생이 다시 요청하면 새로 만들지 않고 기존 attempt를 그대로 반환합니다(멱등). 응시기간이 아니면 실패합니다.")
    @PostMapping("/attempts")
    public ApiResponse<AssessmentAttemptResponse> startAttempt(
            @PathVariable Integer roundId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentIntroService.startAttempt(roundId, authUser.getId()));
    }
}
