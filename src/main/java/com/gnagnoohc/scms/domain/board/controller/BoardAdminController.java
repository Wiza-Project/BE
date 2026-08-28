package com.gnagnoohc.scms.domain.board.controller;

import com.gnagnoohc.scms.domain.board.dto.request.BoardPostCreateRequest;
import com.gnagnoohc.scms.domain.board.dto.request.BoardPostUpdateRequest;
import com.gnagnoohc.scms.domain.board.dto.response.BoardPostDetailResponse;
import com.gnagnoohc.scms.domain.board.service.BoardService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 공지사항/FAQ 게시글 관리 API. SecurityConfig가 "/api/admin/**"을 hasRole("STAFF")로 이미 거르므로
 * 이 컨트롤러에는 별도 인가 어노테이션이 없다. 부서별 추가 제한은 이번 스코프에 없다.
 *
 * FAQ 카테고리는 새 테이블 대신 CommonCode(FAQ_CATEGORY 그룹)를 그대로 참조하므로, 카테고리
 * 등록/수정/삭제 API는 이 컨트롤러에 없다 - 카테고리 자체를 관리하려면 공통코드 관리 수단을 쓴다.
 *
 * 게시글 등록/수정은 첨부파일 유무에 따라 두 가지 요청 형태를 모두 받는다:
 *   - 첨부 없음: 순수 JSON (Content-Type: application/json)
 *   - 첨부 있음: multipart/form-data, "request" part(JSON)와 "files" part(File[])
 * 같은 경로+메서드에 consumes만 다른 두 핸들러를 등록해 Content-Type으로 자동 분기한다.
 */
@Tag(name = "BoardAdmin", description = "공지사항/FAQ 게시글 관리(교직원)")
@RestController
@RequestMapping("/api/admin/boards/{boardType}")
@RequiredArgsConstructor
public class BoardAdminController {

    private final BoardService boardService;

    @Operation(summary = "게시글 등록(첨부 없음)")
    @PostMapping(value = "/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardPostDetailResponse> createPost(
            @PathVariable String boardType,
            @Valid @RequestBody BoardPostCreateRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(boardService.createPost(boardType, request, null, authUser.getId()));
    }

    @Operation(summary = "게시글 등록(첨부 포함)", description = "request part(JSON)와 files part(File[])로 함께 보냅니다")
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardPostDetailResponse> createPostWithFiles(
            @PathVariable String boardType,
            @Valid @RequestPart("request") BoardPostCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(boardService.createPost(boardType, request, files, authUser.getId()));
    }

    @Operation(summary = "게시글 수정(첨부 변경 없음)", description = "removeFileIds로 기존 첨부 중 일부만 삭제할 수 있습니다")
    @PatchMapping(value = "/posts/{postId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BoardPostDetailResponse> updatePost(
            @PathVariable String boardType,
            @PathVariable Integer postId,
            @Valid @RequestBody BoardPostUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(boardService.updatePost(boardType, postId, request, null, authUser.getId()));
    }

    @Operation(summary = "게시글 수정(첨부 추가 포함)", description = "request part(JSON)와 files part(File[])로 함께 보냅니다")
    @PatchMapping(value = "/posts/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BoardPostDetailResponse> updatePostWithFiles(
            @PathVariable String boardType,
            @PathVariable Integer postId,
            @Valid @RequestPart("request") BoardPostUpdateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(boardService.updatePost(boardType, postId, request, files, authUser.getId()));
    }

    @Operation(summary = "게시글 삭제(soft delete)")
    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable String boardType, @PathVariable Integer postId) {
        boardService.deletePost(boardType, postId);
    }
}
