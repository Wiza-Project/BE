package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingTypeResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingTypeService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상담사 일정 등록 화면에서 선택할 상담 유형 목록을 조회하는 HTTP 요청을 처리한다.
 *
 * <p>학생용 {@code /api/students/counseling-types}와 응답 형태(DTO)는 같지만 노출 대상이 다르다.
 * 상담사는 일정을 붙일 수 있는 DIRECT 유형만 필요하므로 서비스가 그 목록만 내려준다.</p>
 *
 * <p>인가는 {@code SecurityConfig}의 URL 규칙(<code>/api/counselors/** → hasRole("ST200")</code>)이 담당한다.
 * 학생·비상담사 토큰은 이 경로에 닿기 전에 403으로 걸러지므로 메서드 보안을 별도로 걸지 않는다.</p>
 */
@RestController
@RequestMapping("/api/counselors/counseling-types")
@RequiredArgsConstructor
public class CounselorCounselingTypeController {

    private final CounselingTypeService counselingTypeService;

    /**
     * 상담사가 새 일정을 열 수 있는 활성 DIRECT 상담 유형 목록을 조회한다.
     * 목록 자체가 이미 로그인한 상담사의 역할 범위(ST200 단독/ST200+ST300)로 제한되므로,
     * FE는 역할별로 상담 유형을 따로 판정하지 않고 이 응답을 그대로 써도 된다.
     */
    @GetMapping
    public ApiResponse<List<CounselingTypeResponse>> getSchedulableCounselingTypes(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingTypeService.getSchedulableCounselingTypes(authUser.getId()));
    }
}
