package com.gnagnoohc.scms.domain.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 등록 요청. boardType은 URL 경로에서 받으므로 body에는 없다.
 * categoryCode는 FAQ에서만, moduleCode는 NOTICE에서만 의미가 있다(다른 쪽에 보내면 무시).
 * postStatus는 DRAFT/PUBLISHED/HIDDEN 중 하나이며, 비우면 PUBLISHED로 등록한다.
 * 첨부파일은 이 DTO가 아니라 multipart 요청의 별도 "files" part로 받는다(BoardAdminController 참고).
 */
public record BoardPostCreateRequest(
        @NotBlank @Size(max = 300) String title,
        @NotBlank String content,
        @Size(max = 40) String categoryCode,
        @Size(max = 30) String moduleCode,
        boolean pinned,
        String postStatus
) {
}
