package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageActivityTypeRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageActivityTypeResponseDTO;
import com.gnagnoohc.scms.domain.mileage.service.MileageActivityTypeService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** 마일리지 활동 유형 등록/조회. 관리자(AD100)만 사용할 수 있다. */
@Tag(name = "MileageActivityTypeAdmin", description = "마일리지 활동 유형 관리")
@RestController
@RequestMapping("/api/admin/mileage/activity-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AD100')")
public class MileageActivityTypeAdminController {

    private final MileageActivityTypeService mileageActivityTypeService;

    @Operation(summary = "활동 유형 등록", description = "마일리지를 적립할 활동 유형을 등록합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MileageActivityTypeResponseDTO> register(
            @Valid @RequestBody MileageActivityTypeRegisterRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(mileageActivityTypeService.register(request, authUser.getId()));
    }

    @Operation(summary = "활동 유형 목록 조회", description = "정책 등록 화면에서 사용할 활성화된 마일리지 활동 유형 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<MileageActivityTypeResponseDTO>> list() {
        return ApiResponse.ok(mileageActivityTypeService.listActive());
    }
}
