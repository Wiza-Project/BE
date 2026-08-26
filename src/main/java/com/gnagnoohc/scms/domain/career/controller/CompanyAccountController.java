package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.company.*;
import com.gnagnoohc.scms.domain.career.service.CompanyAccountService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 협약기업 메타데이터 등록, 조회 및 교직원 심사 검증 REST 컨트롤러.
 *
 * @author YUN
 */
@Tag(name = "Career - 협약기업 메타데이터 및 심사 API", description = "협약기업 정보 등록, 조회 및 취창업부서 교직원 검증 API")
@RestController
@RequestMapping("/api/v1/career/companies")
@RequiredArgsConstructor
public class CompanyAccountController {

    private final CompanyAccountService companyAccountService;

    @Operation(summary = "협약기업 정보 등록 및 제휴 신청", description = "기업 메타데이터와 사업자등록번호를 입력하여 등록 신청을 접수합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Integer>> registerCompany(@Valid @RequestBody CompanyRegisterRequestDTO requestDTO) {
        Integer companyAccountId = companyAccountService.registerCompany(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(companyAccountId));
    }

    @Operation(summary = "기업 단건 상세 조회", description = "지정된 기업의 상세 메타데이터 및 심사 승인 상태를 조회합니다.")
    @GetMapping("/{companyAccountId}")
    public ResponseEntity<ApiResponse<CompanyDetailResponseDTO>> getCompanyDetail(@PathVariable Integer companyAccountId) {
        CompanyDetailResponseDTO response = companyAccountService.getCompanyDetail(companyAccountId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "[교직원 전용] 기업 목록 검색 및 페이징 조회", description = "승인 상태, 기업명 등의 필터 조건으로 기업 목록을 페이징 조회합니다.")
    @GetMapping
    @PreAuthorize("@careerSecurity.isCareerStaff(principal)")
    public ResponseEntity<ApiResponse<Page<CompanySummaryResponseDTO>>> searchCompanies(
            @ModelAttribute CompanySearchConditionDTO conditionDTO,
            @PageableDefault(size = 10, sort = "companyAccountId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CompanySummaryResponseDTO> page = companyAccountService.searchCompanies(conditionDTO, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @Operation(summary = "[교직원 전용] 기업 승인/반려 심사 처리", description = "취창업지원과 교직원이 기업의 등록 승인(VERIFIED) 또는 반려(REJECTED)를 처리합니다.")
    @PatchMapping("/{companyAccountId}/verify")
    @PreAuthorize("@careerSecurity.isCareerStaff(principal)")
    public ResponseEntity<ApiResponse<Void>> verifyCompany(
            @PathVariable Integer companyAccountId,
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CompanyVerifyRequestDTO requestDTO) {

        companyAccountService.verifyCompany(companyAccountId, authUser.getId(), requestDTO);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}