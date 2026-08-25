package com.gnagnoohc.scms.domain.user.controller;

import com.gnagnoohc.scms.domain.user.dto.consent.ConsentAgreementRequest;
import com.gnagnoohc.scms.domain.user.dto.consent.ConsentPolicyResponse;
import com.gnagnoohc.scms.domain.user.dto.consent.UserConsentHistoryResponse;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentPolicyService;
import com.gnagnoohc.scms.domain.user.service.consent.UserConsentService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 개인정보/약관 동의 공통 모듈의 진입점. 학생뿐 아니라 로그인한 모든 사용자 유형(교직원/상담사/
 * 기업체)이 쓸 수 있어야 해서 /api/students/** 가 아닌 별도 경로로 둔다. 이 경로는
 * SecurityConfig의 그 어떤 hasRole 패턴에도 걸리지 않고 마지막 anyRequest().authenticated() 로만
 * 걸러지므로, 이 컨트롤러를 추가하기 위해 SecurityConfig를 수정할 필요가 없다.
 *
 * 다른 도메인(상담/취업창업/핵심역량진단)이 자기 업무 흐름 중에 동의를 검증/참조하는 것은
 * 이 컨트롤러가 아니라 {@link com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier}
 * 를 직접 주입받아 처리한다 — 여기 엔드포인트는 사용자가 "동의 자체"를 관리(조회/동의/철회)하는
 * 화면 전용이다.
 */
@Tag(name = "Consent", description = "약관/개인정보 동의 조회 · 동의 · 철회")
@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentPolicyService consentPolicyService;
    private final UserConsentService userConsentService;

    @Operation(summary = "동의 정책 목록 조회", description = "moduleCode(COMMON/ASSESSMENT/COUNSELING/CAREER) 기준으로 현재 유효한 정책을 유형별로 내려준다.")
    @GetMapping("/policies")
    public ApiResponse<List<ConsentPolicyResponse>> getPolicies(@RequestParam String moduleCode) {
        return ApiResponse.ok(consentPolicyService.getEffectivePolicies(moduleCode));
    }

    @Operation(summary = "내 동의 이력 조회", description = "철회 이력을 포함해 본인의 동의 이력 전체를 최신순으로 내려준다.")
    @GetMapping("/me")
    public ApiResponse<List<UserConsentHistoryResponse>> getMyHistory(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(userConsentService.getMyHistory(authUser.getId()));
    }

    @Operation(summary = "동의", description = "정책 한 건에 동의를 기록한다. 이미 유효하게 동의한 정책이면 새로 만들지 않고 기존 이력을 그대로 반환한다(멱등).")
    @PostMapping
    public ApiResponse<UserConsentHistoryResponse> agree(
            @Valid @RequestBody ConsentAgreementRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(userConsentService.agree(authUser.getId(), request.consentPolicyId()));
    }

    @Operation(summary = "동의 철회", description = "본인 동의 1건을 철회 처리한다. 필수 동의라도 철회 자체는 막지 않는다 — 이후 이용 제한 여부는 각 기능 화면에서 안내한다.")
    @DeleteMapping("/{userConsentId}")
    public ApiResponse<Void> withdraw(
            @PathVariable Integer userConsentId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        userConsentService.withdraw(authUser.getId(), userConsentId);
        return ApiResponse.ok();
    }
}
