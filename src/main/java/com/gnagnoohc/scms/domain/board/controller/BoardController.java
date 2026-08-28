package com.gnagnoohc.scms.domain.board.controller;

import com.gnagnoohc.scms.domain.board.dto.response.BoardCategoryResponse;
import com.gnagnoohc.scms.domain.board.dto.response.BoardPostDetailResponse;
import com.gnagnoohc.scms.domain.board.dto.response.BoardPostListItemResponse;
import com.gnagnoohc.scms.domain.board.service.BoardService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공지사항/FAQ 공통 조회 API. 로그인한 사용자면 누구나 접근 가능하다(SecurityConfig의 기본 인증 규칙).
 * STAFF는 임시저장/숨김 글까지 함께 보인다 - 관리 화면에서 별도 조회 API 없이 이 엔드포인트를 재사용하기 위함.
 *
 * boardType은 board_post.board_type 값 그대로(NOTICE/FAQ) 받는다. moduleCode는 NOTICE 목록/필터에서만,
 * categoryCode는 FAQ 목록/필터에서만 의미가 있다.
 */
@Tag(name = "Board", description = "공지사항/FAQ 게시글·카테고리 조회")
@RestController
@RequestMapping("/api/boards/{boardType}")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "게시글 목록 조회", description = "boardType(NOTICE/FAQ) 게시판의 게시글을 페이지 단위로 조회합니다")
    @GetMapping("/posts")
    public ApiResponse<PageResponse<BoardPostListItemResponse>> list(
            @PathVariable String boardType,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(
                boardService.list(boardType, moduleCode, categoryCode, keyword, isStaff(authUser), pageable));
    }

    @Operation(summary = "게시글 상세 조회", description = "boardType(NOTICE/FAQ) 게시판의 게시글 상세(첨부파일 포함)를 조회합니다")
    @GetMapping("/posts/{postId}")
    public ApiResponse<BoardPostDetailResponse> getDetail(
            @PathVariable String boardType,
            @PathVariable Integer postId,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(boardService.getDetail(boardType, postId, isStaff(authUser)));
    }

    @Operation(summary = "카테고리 목록 조회", description = "FAQ 카테고리(공통코드 FAQ_CATEGORY 그룹)를 정렬 순서대로 조회합니다. NOTICE는 항상 빈 배열입니다.")
    @GetMapping("/categories")
    public ApiResponse<List<BoardCategoryResponse>> listCategories(@PathVariable String boardType) {
        return ApiResponse.ok(boardService.listCategories(boardType));
    }

    private boolean isStaff(AuthUser authUser) {
        return "STAFF".equals(authUser.getUserType());
    }
}
