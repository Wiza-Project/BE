package com.gnagnoohc.scms.domain.board.dto.response;

import com.gnagnoohc.scms.global.common.entity.BoardPost;
import com.gnagnoohc.scms.global.common.entity.PostStatus;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;

import java.time.OffsetDateTime;
import java.util.Map;

public record BoardPostListItemResponse(
        Integer postId,
        String categoryCode,
        String categoryName,
        String title,
        String authorName,
        boolean pinned,
        String postStatus,
        String postStatusLabel,
        boolean hasAttachment,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt
) {
    /** categoryNames: FAQ_CATEGORY 그룹 code→codeName 맵(CommonCodeService.getCodeNameMap). NOTICE 목록에는 빈 맵을 넘기면 된다. */
    public static BoardPostListItemResponse from(BoardPost post, Map<String, String> categoryNames) {
        // published_at 컬럼이 없어 createdAt을 발행 시각으로 대체한다 - PUBLISHED 상태일 때만 노출한다
        // (DRAFT/HIDDEN 글에 "발행일"을 보여주면 오해의 소지가 있어 null로 감춘다).
        boolean published = post.getPostStatusEnum() == PostStatus.PUBLISHED;
        return new BoardPostListItemResponse(
                post.getBoardPostId(),
                post.getCategoryCode(),
                post.getCategoryCode() == null ? null : categoryNames.get(post.getCategoryCode()),
                post.getTitle(),
                post.getAuthorUser().getUserName(),
                post.isPinned(),
                post.getPostStatus(),
                post.getPostStatusEnum().getLabel(),
                post.getFileGroup() != null,
                published ? DateTimeUtils.toKstOffsetDateTime(post.getCreatedAt()) : null,
                DateTimeUtils.toKstOffsetDateTime(post.getUpdatedAt()));
    }
}
