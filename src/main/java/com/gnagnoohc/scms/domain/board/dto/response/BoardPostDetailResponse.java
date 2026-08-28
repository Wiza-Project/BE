package com.gnagnoohc.scms.domain.board.dto.response;

import com.gnagnoohc.scms.global.common.entity.BoardPost;
import com.gnagnoohc.scms.global.common.entity.PostStatus;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;

import java.time.OffsetDateTime;
import java.util.List;

public record BoardPostDetailResponse(
        Integer postId,
        String boardType,
        String moduleCode,
        String categoryCode,
        String categoryName,
        String title,
        String content,
        String authorName,
        boolean pinned,
        String postStatus,
        String postStatusLabel,
        List<BoardAttachmentResponse> attachments,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** categoryName: FAQ 글이 아니면 null을 넘기면 된다. */
    public static BoardPostDetailResponse from(BoardPost post, List<BoardAttachmentResponse> attachments,
                                                   String categoryName) {
        boolean published = post.getPostStatusEnum() == PostStatus.PUBLISHED;
        return new BoardPostDetailResponse(
                post.getBoardPostId(),
                post.getBoardType(),
                post.getModuleCode(),
                post.getCategoryCode(),
                categoryName,
                post.getTitle(),
                post.getContent(),
                post.getAuthorUser().getUserName(),
                post.isPinned(),
                post.getPostStatus(),
                post.getPostStatusEnum().getLabel(),
                attachments,
                published ? DateTimeUtils.toKstOffsetDateTime(post.getCreatedAt()) : null,
                DateTimeUtils.toKstOffsetDateTime(post.getCreatedAt()),
                DateTimeUtils.toKstOffsetDateTime(post.getUpdatedAt()));
    }
}
