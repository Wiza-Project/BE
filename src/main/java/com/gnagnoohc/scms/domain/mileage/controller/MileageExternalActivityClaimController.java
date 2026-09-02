package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageExternalActivityClaimRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageEvidenceFileUploadResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageExternalActivityClaimResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageExternalActivityPolicyResponse;
import com.gnagnoohc.scms.domain.mileage.service.MileageExternalActivityClaimService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** 학생의 외부활동 증빙 업로드와 마일리지 신청 제출 API다. */
@Tag(name = "MileageExternalActivity", description = "학생 외부활동 마일리지 신청")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/mileage/external-activities")
@PreAuthorize("hasRole('STUDENT')")
public class MileageExternalActivityClaimController {

    private final MileageExternalActivityClaimService mileageExternalActivityClaimService;

    @Operation(summary = "학생 외부활동 마일리지 정책 조회", description = "현재 날짜에 적용 가능한 활성 외부활동 정책을 활동 유형별 최신 버전으로 조회합니다.")
    @GetMapping("/policies")
    public ApiResponse<List<MileageExternalActivityPolicyResponse>> listPolicies(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate activityDate
    ) {
        return ApiResponse.ok(mileageExternalActivityClaimService.listAvailablePolicies(activityDate));
    }

    @Operation(summary = "외부활동 증빙 파일 업로드", description = "PDF 증빙 파일 1개를 업로드하고 신청에 사용할 fileGroupId를 발급합니다.")
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MileageEvidenceFileUploadResponse> uploadEvidence(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageExternalActivityClaimService.uploadEvidence(
                file, authUser.getId()));
    }

    @Operation(summary = "외부활동 마일리지 신청 제출", description = "업로드한 증빙 fileGroupId와 활동 정보를 저장하고 심사 대기 상태로 접수합니다.")
    @PostMapping(value = "/applications", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MileageExternalActivityClaimResponse> submit(
            @Valid @RequestBody MileageExternalActivityClaimRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageExternalActivityClaimService.submit(
                request, authUser.getId()));
    }
}
