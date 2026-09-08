package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselorStudentLookupRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorStudentLookupResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselorReservationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상담사가 학번으로 활성 학생 한 명을 정확히 조회한다.
 * 학번은 개인정보라 URL 쿼리스트링(GET)이 아닌 POST 요청 본문으로만 받는다.
 * 학생 ID·상담사 ID는 요청으로 받지 않고 인증 정보와 서비스 검증으로만 정해지므로 다른 상담사나
 * 학생을 가장할 수 없다. 학번 trim·길이 검증은 서비스가 최종적으로 책임진다.
 */
@RestController
@RequestMapping("/api/counselors/students")
@RequiredArgsConstructor
public class CounselorStudentController {

    private final CounselorReservationService counselorReservationService;

    @PostMapping("/lookup")
    public ApiResponse<CounselorStudentLookupResponse> lookup(
            @RequestBody CounselorStudentLookupRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                counselorReservationService.lookupStudent(authUser.getId(), request.universityNo())
        );
    }
}
