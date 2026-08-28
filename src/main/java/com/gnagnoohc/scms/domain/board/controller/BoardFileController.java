package com.gnagnoohc.scms.domain.board.controller;

import com.gnagnoohc.scms.domain.board.service.BoardService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 게시판 첨부파일 다운로드. FileGroup은 이력서/포트폴리오 등 다른 도메인과도 공유하는 테이블이라,
 * BoardService.downloadFile()이 storedFileId가 실제로 "게시판 글"에 연결돼 있고 지금 로그인한
 * 사용자가 그 글을 볼 수 있는지까지 확인한 뒤에만 파일을 내려준다.
 */
@Tag(name = "Board", description = "게시판 첨부파일 다운로드")
@RestController
@RequiredArgsConstructor
public class BoardFileController {

    private final BoardService boardService;

    @Operation(summary = "첨부파일 다운로드")
    @GetMapping("/api/files/{storedFileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Integer storedFileId,
            @AuthenticationPrincipal AuthUser authUser) {
        FileStorageService.LoadedFile loaded =
                boardService.downloadFile(storedFileId, "STAFF".equals(authUser.getUserType()));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(loaded.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(loaded.originalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(loaded.resource());
    }
}
