package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.response.ProgramListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.service.ProgramService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "StudentProgram", description = "학생의 비교과 프로그램 목록 조회")
@RestController
@RequestMapping("/api/students/programs")
@RequiredArgsConstructor
public class StudentProgramController {

    private final ProgramService programService;

    @Operation(summary = "프로그램 목록 조회", description = "상태/이름 키워드로 비교과 프로그램 목록을 페이지 단위로 조회합니다")
    // HTTP GET 요청, 즉 "/api/students/programs" 로 오는 요청을 이 메서드가 처리한다.
    @GetMapping
    public ApiResponse<PageResponse<ProgramListItemResponseDTO>> list(
            // 모집중/운영중/종료 중 하나로 필터링. 생략하면 전체 상태를 조회한다.
            @RequestParam(required = false) ProgramStatus status,
            // 프로그램명 부분 일치(대소문자 무시) 검색어. 생략하면 이름으로 거르지 않는다.
            @RequestParam(required = false) String keyword,
            // page/size/sort 쿼리 파라미터를 스프링이 자동으로 Pageable로 변환한다.
            // 기본값은 최신 등록순 20건씩.
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(programService.list(status, keyword, pageable));
    }
}
