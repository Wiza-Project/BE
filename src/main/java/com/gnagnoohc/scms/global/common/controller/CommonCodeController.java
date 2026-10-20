package com.gnagnoohc.scms.global.common.controller;

import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.CommonCodeResponseDTO;
import com.gnagnoohc.scms.global.common.service.CommonCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공통코드 조회. 프로그램 유형·학년도·학기 등 여러 도메인이 공유하는 드롭다운/옵션 값을
 * 그룹코드(codeGroup) 단위로 내려준다.
 * 관리자 화면과 함께 별도 티켓으로 진행.
 */
@Tag(name = "CommonCode", description = "공통코드 조회")
@RestController
@RequestMapping("/api/common-codes")
@RequiredArgsConstructor
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    @Operation(summary = "공통코드 조회", description = "그룹코드(groupCode)로 활성 상태인 공통코드를 정렬 순서(sortOrder)대로 조회합니다.")
    @GetMapping
    public ApiResponse<List<CommonCodeResponseDTO>> list(
            @RequestParam(value = "groupCode", required = false) String groupCode) {
        return ApiResponse.ok(commonCodeService.getCodes(groupCode));
    }
}
