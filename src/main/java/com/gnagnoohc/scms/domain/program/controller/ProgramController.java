package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.response.CompetencyOptionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAdminListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramRegisterResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramUpdateResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.service.ProgramService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Program", description = "비교과프로그램 등록/수정/삭제")
@RestController
@RequestMapping("/api/admin/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @Operation(summary = "담당 프로그램 목록 조회", description = "로그인한 staff 본인이 담당한 비교과 프로그램 목록을 페이지 단위로 조회합니다")
    @GetMapping
    public ApiResponse<PageResponse<ProgramAdminListItemResponseDTO>> list(
            @RequestParam(required = false) ProgramStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programService.listMine(authUser.getId(), status, keyword, pageable));
    }

    @Operation(summary = "프로그램 등록", description = "비교과 프로그램을 신규 등록합니다 (모집중 상태로 시작)")
    // HTTP POST 요청, 즉 "/api/admin/programs" 로 오는 요청을 이 메서드가 처리한다.
    @PostMapping
    // 등록에 성공하면 HTTP 상태코드로 200(OK) 대신 201(CREATED, "새로 생성됨")을 응답한다.
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProgramRegisterResponseDTO> register(
            // @Valid: request 안의 @NotNull, @NotBlank 같은 검증 어노테이션들을 실제로 검사하라는 표시.
            // @RequestBody: HTTP 요청 body에 담긴 JSON을 ProgramRegisterRequestDTO 객체로 자동 변환.
            @Valid @RequestBody ProgramRegisterRequestDTO request,
            // @AuthenticationPrincipal: 로그인 토큰(JWT 등)에서 스프링 시큐리티가 미리 뽑아둔
            // "지금 로그인한 사용자 정보"를 그대로 꺼내온다. 클라이언트가 body에 넣어 보내는 값이 아니므로 위조가 불가능하다.
            @AuthenticationPrincipal AuthUser authUser) {
        // authUser.getId()로 등록 담당자를 서버가 직접 결정해서 서비스에 넘긴다.
        return ApiResponse.ok(programService.register(request, authUser.getId(), authUser.getDepartmentCodeId()));
    }

    @Operation(summary = "핵심역량 옵션 조회", description = "프로그램 등록 폼에서 사용할 핵심역량 목록을 조회합니다")
    @GetMapping("/competencies")
    public ApiResponse<List<CompetencyOptionResponseDTO>> listCompetencyOptions() {
        return ApiResponse.ok(programService.getCompetencyOptions());
    }

    @Operation(summary = "프로그램 수정", description = "모집중 상태의 비교과 프로그램을 전체 필드 수정합니다 (등록자 본인만 가능)")
    // HTTP PUT 요청, 즉 "/api/admin/programs/{programId}" 로 오는 요청을 이 메서드가 처리한다.
    // PUT은 "이 리소스 전체를 이 내용으로 통째로 교체해줘"라는 의미의 HTTP 메서드다(일부 필드만 보내는 PATCH와 다름).
    @PutMapping("/{programId}")
    public ApiResponse<ProgramUpdateResponseDTO> update(
            // @PathVariable: URL 경로 중 "{programId}" 부분에 실제로 들어온 값을 그대로 매개변수로 받는다.
            // 예를 들어 요청이 "/api/admin/programs/5"라면 programId에는 5가 담긴다.
            @PathVariable Integer programId,
            // 등록 때와 마찬가지로, 요청 body(JSON)를 검증하면서 ProgramUpdateRequestDTO로 변환한다.
            @Valid @RequestBody ProgramUpdateRequestDTO request,
            // 지금 로그인한 사용자 정보. 서비스 계층에서 "이 프로그램을 등록한 사람과 같은 사람인지" 확인하는 데 쓰인다.
            @AuthenticationPrincipal AuthUser authUser) {
        // authUser.getId()를 그대로 서비스에 넘겨서, 소유자 검증(본인이 등록한 프로그램인지)을 서비스 계층에서 수행하게 한다.
        return ApiResponse.ok(programService.update(programId, request, authUser.getId()));
    }

    @Operation(summary = "프로그램 삭제", description = "모집중인 비교과 프로그램을 삭제합니다 (등록자 본인만 가능)")
    // HTTP DELETE 요청, 즉 "/api/admin/programs/{programId}" 로 오는 요청을 이 메서드가 처리한다.
    @DeleteMapping("/{programId}")
    // 삭제에 성공하면 돌려줄 데이터가 없으므로 204(NO_CONTENT)로 응답한다.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Integer programId,
            // 지금 로그인한 사용자 정보. 서비스 계층에서 "이 프로그램을 등록한 사람과 같은 사람인지" 확인하는 데 쓰인다.
            @AuthenticationPrincipal AuthUser authUser) {
        programService.delete(programId, authUser.getId());
    }
}
