package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingPublicResultCorrectionRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingPublicResultHistoryResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingPublicResultSaveRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorCounselingPublicResultResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingPublicResultService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상담사가 자신의 배정에 딸린 회기의 공개 상담 결과를 조회·초안 저장·일반 공개하고, 마지막 회기
 * 결과로 예약을 최종 완료한다. 상담사 ID는 인증된 사용자 정보에서만 꺼내며 엔티티는 반환하지 않는다.
 */
@RestController
@RequestMapping("/api/counselors")
@RequiredArgsConstructor
public class CounselorCounselingPublicResultController {

    private final CounselingPublicResultService counselingPublicResultService;

    /**
     * 공개 결과를 조회한다. 현재 담당자뿐 아니라 배정이 끝난 과거 담당자도 자신이 맡았던 회기라면
     * 조회할 수 있다. 결과가 없으면 예외 없이 resultStatus=EMPTY로 응답한다.
     */
    @GetMapping("/counseling-sessions/{sessionId}/public-result")
    public ApiResponse<CounselorCounselingPublicResultResponse> getResult(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.getResult(sessionId, authUser.getId()));
    }

    /**
     * 초안을 저장한다. 결과가 없으면 새로 만들고, 있으면 같은 행의 내용만 바꾼다.
     * 공개된 결과의 수정은 서비스에서 409(S010)로 거절된다.
     */
    @PutMapping("/counseling-sessions/{sessionId}/public-result")
    public ApiResponse<CounselorCounselingPublicResultResponse> saveDraft(
            @PathVariable Integer sessionId,
            @Valid @RequestBody CounselingPublicResultSaveRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.saveDraft(
                sessionId, request.resultSummary(), request.actionPlan(), authUser.getId()
        ));
    }

    /**
     * 저장된 초안을 학생에게 일반 공개한다. 요청 본문은 없다(이미 저장된 초안 그대로 공개).
     * 예약 상태와 활성 배정은 바뀌지 않는다.
     */
    @PatchMapping("/counseling-sessions/{sessionId}/public-result/publish")
    public ApiResponse<CounselorCounselingPublicResultResponse> publish(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.publish(sessionId, authUser.getId()));
    }

    /**
     * 이 회기 결과를 최종 결과로 확정하고 예약을 완료한다. 요청 본문은 없다. 필요하면 결과 공개까지
     * 같은 트랜잭션에서 함께 처리되며, 완료 조건은 서비스가 잠금 후 다시 검증한다.
     */
    @PatchMapping("/counseling-sessions/{sessionId}/public-result/complete")
    public ApiResponse<CounselorCounselingPublicResultResponse> complete(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.complete(sessionId, authUser.getId()));
    }

    /**
     * 이미 공개된 최신 결과를 새 버전으로 정정한다. 원본 행은 수정하지 않고 versionNo+1을 즉시
     * 공개한다. 배정이 끝난 원래 담당자도 요청할 수 있다(설계 문서 3절의 유일한 쓰기 예외).
     */
    @PostMapping("/counseling-sessions/{sessionId}/public-result/corrections")
    public ApiResponse<CounselorCounselingPublicResultResponse> correct(
            @PathVariable Integer sessionId,
            @Valid @RequestBody CounselingPublicResultCorrectionRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.correct(
                sessionId, request.expectedVersionNo(), request.resultSummary(),
                request.actionPlan(), request.correctionReason(), authUser.getId()
        ));
    }

    /**
     * 공개된 모든 버전을 versionNo 내림차순으로 반환한다(정정 사유·작성자 포함, 학생에게는 노출 안 함).
     * 페이지 래퍼 없이 배열 그대로 내려준다.
     */
    @GetMapping("/counseling-sessions/{sessionId}/public-result/history")
    public ApiResponse<List<CounselingPublicResultHistoryResponse>> getHistory(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.getHistory(sessionId, authUser.getId()));
    }
}
