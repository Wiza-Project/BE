package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingPrivateRecordResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingPrivateRecordSaveRequest;
import com.gnagnoohc.scms.domain.counsel.service.CounselingPrivateRecordService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상담사가 자신의 배정에 딸린 회기의 비공개 상담 기록(원문)을 조회·임시저장·확정한다.
 * 상담사 ID는 인증된 사용자 정보에서만 꺼낸다. 원문은 엔티티 반환 없이 응답 DTO로만 노출한다.
 */
@RestController
@RequestMapping("/api/counselors")
@RequiredArgsConstructor
public class CounselorCounselingPrivateRecordController {

    private final CounselingPrivateRecordService counselingPrivateRecordService;

    @GetMapping("/counseling-sessions/{sessionId}/private-record")
    public ApiResponse<CounselingPrivateRecordResponse> getRecord(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPrivateRecordService.getRecord(sessionId, authUser.getId()));
    }

    @PutMapping("/counseling-sessions/{sessionId}/private-record")
    public ApiResponse<CounselingPrivateRecordResponse> saveDraft(
            @PathVariable Integer sessionId,
            @Valid @RequestBody CounselingPrivateRecordSaveRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPrivateRecordService.saveDraft(
                sessionId, request.privateContent(), authUser.getId()
        ));
    }

    @PatchMapping("/counseling-sessions/{sessionId}/private-record/confirm")
    public ApiResponse<CounselingPrivateRecordResponse> confirm(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPrivateRecordService.confirm(sessionId, authUser.getId()));
    }
}
