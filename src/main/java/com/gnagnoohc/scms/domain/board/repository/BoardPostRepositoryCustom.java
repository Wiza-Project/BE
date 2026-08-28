package com.gnagnoohc.scms.domain.board.repository;

import com.gnagnoohc.scms.global.common.entity.BoardPost;
import com.gnagnoohc.scms.global.common.entity.BoardType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BoardPostRepositoryCustom {

    /**
     * boardType 게시글을 검색한다. allowedStatuses에 속한 post_status만 대상이며,
     * 삭제된(deleted_at IS NOT NULL) 글은 항상 제외한다. moduleCode는 NOTICE에서만,
     * categoryCode는 FAQ에서만 실질적인 필터로 쓰인다.
     * 정렬은 boardType에 따라 갈린다:
     *   NOTICE - 상단 고정(pinned) 우선 → 등록일(createdAt) 내림차순 (published_at 컬럼이 없어 대체)
     *   FAQ    - 카테고리(common_code.sort_order) → pinned → 수정일(updatedAt) 내림차순
     */
    Page<BoardPost> search(BoardType boardType, String moduleCode, String categoryCode, String keyword,
                            List<String> allowedStatuses, Pageable pageable);

    // 상세 조회용. author를 fetch join으로 함께 가져온다.
    Optional<BoardPost> findDetail(Integer boardPostId);
}
