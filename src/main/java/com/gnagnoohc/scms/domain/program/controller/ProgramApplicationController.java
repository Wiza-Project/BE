package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramApplicationCancelRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationCancelResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationSummaryResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationSurveyResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplyResponseDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramApplicationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ProgramApplication", description = "학생의 비교과 프로그램 참여 신청")
@RestController
@RequestMapping("/api/students/programs")
@RequiredArgsConstructor
public class ProgramApplicationController {

    private final ProgramApplicationService programApplicationService;

    @Operation(summary = "프로그램 참여 신청", description = "비교과 프로그램에 참여 신청합니다. 정원 초과 시 대기순번이 자동으로 부여됩니다.")
    // HTTP POST 요청, 즉 "/api/students/programs/{programId}/applications" 로 오는 요청을 이 메서드가 처리한다.
    @PostMapping("/{programId}/applications")
    // 신청 접수에 성공하면 HTTP 상태코드로 200(OK) 대신 201(CREATED, "새로 생성됨")을 응답한다.
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProgramApplyResponseDTO> apply(
            // @PathVariable: URL 경로 중 "{programId}" 부분에 실제로 들어온 값을 그대로 매개변수로 받는다.
            @PathVariable Integer programId,
            // @AuthenticationPrincipal: 로그인 토큰에서 스프링 시큐리티가 미리 뽑아둔 "지금 로그인한 사용자 정보"를 꺼내온다.
            // 신청자는 이 값으로만 결정하며, 클라이언트가 body로 보내는 값이 아니므로 위조가 불가능하다.
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programApplicationService.apply(programId, authUser.getId()));
    }

    @Operation(summary = "프로그램 참여 신청 취소", description = "모집 기간이 끝나지 않은 경우에만 참여 신청을 취소할 수 있습니다.")
    @PostMapping("/{programId}/applications/{applicationId}/cancel")
    public ApiResponse<ProgramApplicationCancelResponseDTO> cancel(
            @PathVariable Integer programId,
            @PathVariable Integer applicationId,
            // 취소 사유는 선택 입력이라, 요청 바디 자체를 생략해도(required = false) 된다.
            @RequestBody(required = false) ProgramApplicationCancelRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(programApplicationService.cancel(programId, applicationId, authUser.getId(), reason));
    }

    @Operation(summary = "내 신청 현황 조회", description = "로그인한 학생 본인의 프로그램 참여 신청 현황을 최신 신청순으로 페이지 단위 조회합니다.")
    @GetMapping("/applications")
    public ApiResponse<PageResponse<ProgramApplicationSummaryResponseDTO>> listMyApplications(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(programApplicationService.listMyApplications(authUser.getId(), pageable));
    }

    @Operation(summary = "만족도 설문 완료 처리", description = "본인의 참여 신청 건에 대한 만족도 설문 제출을 완료 처리합니다. 개별 문항 응답 저장은 지원하지 않습니다.")
    @PostMapping("/{programId}/applications/{applicationId}/survey-complete")
    public ApiResponse<ProgramApplicationSurveyResponseDTO> completeSurvey(
            @PathVariable Integer programId,
            @PathVariable Integer applicationId,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programApplicationService.completeSurvey(programId, applicationId, authUser.getId()));
    }
}
