package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageSimulationResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageSimulationRequest;
import com.gnagnoohc.scms.domain.mileage.service.MileageSimulationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 학생의 인증·장학 마일리지 시뮬레이션 API. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/mileage/simulations")
public class MileageSimulationController {

    private final MileageSimulationService mileageSimulationService;

    /** 목표 정책과 시뮬레이션에 사용할 활동 선택지를 조회한다. */
    @GetMapping("/options")
    public ApiResponse<MileageSimulationResponse.Options> getOptions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Integer academicYear,
            @RequestParam String semesterCode
    ) {
        return ApiResponse.ok(mileageSimulationService.getOptions(
                authUser.getId(), academicYear, semesterCode));
    }

    /** 예정 활동을 반영한 예상 마일리지를 계산한다. */
    @PostMapping
    public ApiResponse<MileageSimulationResponse.Result> simulate(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody MileageSimulationRequest request
    ) {
        return ApiResponse.ok(mileageSimulationService.simulate(authUser.getId(), request));
    }
}
