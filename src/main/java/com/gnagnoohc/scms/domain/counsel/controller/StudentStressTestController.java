package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.response.StressTestQuestionsResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.StressTestResultResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.StressTestSubmitRequest;
import com.gnagnoohc.scms.domain.counsel.service.StressTestService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생용 스트레스 심리검사 API 3종의 HTTP 경계다. 로그인 사용자 ID만 서비스로 넘기고
 * 문항·채점·동의·저장 로직은 전부 StressTestService에 둔다.
 * page/size는 @Validated 없이 이 컨트롤러에서 직접 범위를 확인한다(전역 검증 설정을 이 기능만을
 * 위해 바꾸지 않기 위해서다).
 */
@RestController
@RequestMapping("/api/students/psychological-tests/stress")
@RequiredArgsConstructor
public class StudentStressTestController {

    private final StressTestService stressTestService;

    @GetMapping("/questions")
    public ApiResponse<StressTestQuestionsResponse> getQuestions(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(stressTestService.getQuestions(authUser.getId()));
    }

    @PostMapping("/results")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StressTestResultResponse> submit(
            @Valid @RequestBody StressTestSubmitRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(stressTestService.submit(authUser.getId(), request));
    }

    @GetMapping("/results")
    public ApiResponse<PageResponse<StressTestResultResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return ApiResponse.ok(stressTestService.getHistory(authUser.getId(), page, size));
    }
}
