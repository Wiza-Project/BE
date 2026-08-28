package com.gnagnoohc.scms.domain.board.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 게시글 수정 요청(PATCH). null 필드는 "변경하지 않음"을 뜻한다.
 * categoryCode를 비우고 clearCategoryCode를 true로 보내면 카테고리 연결을 해제한다.
 * 새 첨부파일은 이 DTO가 아니라 multipart 요청의 별도 "files" part로 추가하고(BoardAdminController 참고),
 * 기존 첨부파일 중 일부를 지우려면 removeFileIds에 storedFileId를 담아 보낸다.
 */
public record BoardPostUpdateRequest(
        @Size(max = 300) String title,
        String content,
        @Size(max = 40) String categoryCode,
        boolean clearCategoryCode,
        @Size(max = 30) String moduleCode,
        Boolean pinned,
        String postStatus,
        List<Integer> removeFileIds
) {
}
