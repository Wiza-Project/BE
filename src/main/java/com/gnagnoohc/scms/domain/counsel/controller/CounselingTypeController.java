package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingScheduleAvailabilityResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingTypeResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingScheduleService;
import com.gnagnoohc.scms.domain.counsel.service.CounselingTypeService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 학생의 상담 신청 화면에 필요한 상담 유형 조회 HTTP 요청을 처리한다.
 *
 * <p>컨트롤러는 요청 경로를 서비스에 연결하고 결과를 공통 {@link ApiResponse}로 감싸는 역할만 맡는다.
 * 조회 조건과 응답 변환을 서비스에 두어 HTTP 처리와 업무 로직이 섞이지 않게 한다.</p>
 */
@RestController
@RequestMapping("/api/students/counseling-types")
@RequiredArgsConstructor
public class CounselingTypeController {

    private final CounselingTypeService counselingTypeService;
    private final CounselingScheduleService counselingScheduleService;

    /**
     * 신규 상담 신청에 사용할 수 있는 활성 상담 유형 목록을 조회한다.
     */
    @GetMapping
    public ApiResponse<List<CounselingTypeResponse>> getActiveCounselingTypes() {
        return ApiResponse.ok(counselingTypeService.getActiveCounselingTypes());
    }

    /**
     * 로그인한 학생이 선택한 상담 유형에서 실제 예약할 수 있는 일정만 조회한다.
     */
    @GetMapping("/{counselingTypeId}/schedules")
    public ApiResponse<List<CounselingScheduleAvailabilityResponse>> getAvailableSchedules(
            @PathVariable Integer counselingTypeId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                counselingScheduleService.getAvailableSchedules(
                        counselingTypeId,
                        authUser.getId()
                )
        );
    }
}
