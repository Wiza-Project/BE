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

    /**
     * 비공개 기록을 조회한다. 현재 담당자뿐 아니라 배정이 끝난 과거 담당자도 자신이 맡았던
     * 회기라면 조회할 수 있다. 기록이 없으면 예외 없이 recordStatus=EMPTY로 응답한다.
     */
    @GetMapping("/counseling-sessions/{sessionId}/private-record")
    public ApiResponse<CounselingPrivateRecordResponse> getRecord(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPrivateRecordService.getRecord(sessionId, authUser.getId()));
    }

    /**
     * 초안을 임시저장한다. 기록이 없으면 새로 만들고, 있으면 같은 행의 내용만 바꾼다.
     * 이미 확정된 회기는 서비스에서 409(S009)로 거절된다.
     */
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

    /**
     * 저장된 초안을 확정한다. 확정 후에는 수정·재확정이 불가능하므로 요청 본문으로 원문을
     * 다시 받지 않는다(이미 저장된 초안 그대로 확정).
     */
    @PatchMapping("/counseling-sessions/{sessionId}/private-record/confirm")
    public ApiResponse<CounselingPrivateRecordResponse> confirm(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPrivateRecordService.confirm(sessionId, authUser.getId()));
    }
}
