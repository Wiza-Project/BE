package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageActivityTypeResponseDTO;
import com.gnagnoohc.scms.domain.mileage.service.MileageActivityTypeService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 마일리지 정책 등록 화면의 활동 유형 드롭다운용 읽기 전용 조회. 관리자(AD100)만 사용할 수 있다. */
@Tag(name = "MileageActivityTypeAdmin", description = "마일리지 활동 유형 조회")
@RestController
@RequestMapping("/api/admin/mileage/activity-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AD100')")
public class MileageActivityTypeAdminController {

    private final MileageActivityTypeService mileageActivityTypeService;

    @Operation(summary = "활동 유형 목록 조회", description = "정책 등록 화면에서 사용할 활성화된 마일리지 활동 유형 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<MileageActivityTypeResponseDTO>> list() {
        return ApiResponse.ok(mileageActivityTypeService.listActive());
    }
}
