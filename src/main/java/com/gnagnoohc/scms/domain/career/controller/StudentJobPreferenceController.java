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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 학생 취업 희망조건 프레젠테이션 계층 (REST Controller)
 *
 * <p><strong>[보안 및 RESTful API 설계 기준]</strong></p>
 * <ul>
 *   <li><b>접근 권한:</b> SecurityConfig의 {@code /api/students/**} 규칙에 의해 STUDENT 역할 자동 인가</li>
 *   <li><b>사용자 식별:</b> 파라미터 조작 방지를 위해 {@code @AuthenticationPrincipal AuthUser}에서 학생 PK 추출</li>
 *   <li><b>엔드포인트:</b> {@code /api/students/career/preference} (취창업 도메인 표준)</li>
 * </ul>
 *
 * @author YUN
 */
@Tag(name = "03-02. 학생 취업 희망조건 API", description = "학생 본인의 희망직무, 희망지역, 고용형태, 희망연봉 설정 및 조회")
@RestController
@RequestMapping("/api/students/career/preference")
@RequiredArgsConstructor
public class StudentJobPreferenceController {

    private final JobPreferenceService jobPreferenceService;

    /**
     * 로그인한 학생의 취업 희망조건 단건을 조회합니다.
     *
     * @param authUser 인증된 학생 보안 주체 객체
     * @return 취업 희망조건 상세 응답 DTO를 감싼 공통 ApiResponse
     */
    @Operation(summary = "내 취업 희망조건 조회", description = "로그인한 학생의 설정된 취업 희망조건을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<JobPreferenceResponseDTO>> getMyPreference(
            @AuthenticationPrincipal AuthUser authUser) {
        JobPreferenceResponseDTO response = jobPreferenceService.getMyPreference(authUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 로그인한 학생의 취업 희망조건을 신규 등록하거나 기존 설정을 수정합니다.
     *
     * @param authUser   인증된 학생 보안 주체 객체
     * @param requestDTO 희망조건 등록/수정 요청 Body DTO
     * @return 등록/수정 완료된 취업 희망조건 상세 응답 DTO를 감싼 공통 ApiResponse
     */
    @Operation(summary = "내 취업 희망조건 등록 및 수정 (Upsert)", description = "로그인한 학생의 취업 희망조건(희망직무, 희망지역, 고용형태, 연봉)을 등록하거나 수정합니다.")
    @PutMapping
    public ResponseEntity<ApiResponse<JobPreferenceResponseDTO>> upsertMyPreference(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody JobPreferenceRequestDTO requestDTO) {
        JobPreferenceResponseDTO response = jobPreferenceService.upsertPreference(authUser.getId(), requestDTO);
        return ResponseEntity.ok(ApiResponse.ok(response));    }
}