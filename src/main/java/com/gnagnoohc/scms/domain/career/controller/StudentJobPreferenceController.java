package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceResponseDTO;
import com.gnagnoohc.scms.domain.career.service.JobPreferenceService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 학생 취업 희망조건 프레젠테이션 계층 (REST Controller)
 *
 * <p><strong>[보안 및 RESTful API 설계 기준]</strong></p>
 * <ul>
 *   <li><b>인가 및 주체 식별 보안:</b> 클라이언트 요청 파라미터 기반의 식별자 주입을 차단하고,
 *       {@code @AuthenticationPrincipal AuthUser}를 통해 인증 컨텍스트에서 식별자를 직접 추출하여 안전한 접근 제어를 수행</li>
 *   <li><b>접근 권한 통제:</b> {@code @PreAuthorize("hasRole('STUDENT')")}를 선언하여 타 역할군(교직원, 기업)의
 *       비인가 조작을 원천적으로 차단</li>
 *   <li><b>자원 중심 URI 구조:</b> 단일 엔드포인트({@code /api/v1/students/me/preferences})를 기준으로
 *       HTTP Method({@code GET}, {@code PUT})를 매핑하여 명확한 자원 행위 정의</li>
 * </ul>
 *
 * @author YUN
 */
@Tag(name = "03-02. 학생 취업 희망조건 API", description = "학생 본인의 희망지역, 고용형태, 희망연봉 설정 및 조회")
@RestController
@RequestMapping("/api/v1/students/me/preferences")
@RequiredArgsConstructor
public class StudentJobPreferenceController {

    private final JobPreferenceService jobPreferenceService;

    @Operation(summary = "내 취업 희망조건 조회", description = "로그인한 학생의 설정된 취업 희망조건을 조회합니다.")
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<JobPreferenceResponseDTO>> getMyPreference(
            @AuthenticationPrincipal AuthUser authUser) {
        JobPreferenceResponseDTO response = jobPreferenceService.getMyPreference(authUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내 취업 희망조건 등록 및 수정 (Upsert)", description = "로그인한 학생의 취업 희망조건(희망지역, 고용형태, 연봉)을 등록하거나 수정합니다.")
    @PutMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<JobPreferenceResponseDTO>> upsertMyPreference(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody JobPreferenceRequestDTO requestDTO) {
        JobPreferenceResponseDTO response = jobPreferenceService.upsertPreference(authUser.getId(), requestDTO);
        return ResponseEntity.ok(ApiResponse.success("취업 희망조건이 정상적으로 저장되었습니다.", response));
    }
}