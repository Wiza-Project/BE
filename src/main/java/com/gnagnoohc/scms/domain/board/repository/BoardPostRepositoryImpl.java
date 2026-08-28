package com.gnagnoohc.scms.domain.board.repository;

import com.gnagnoohc.scms.domain.board.BoardConstants;
import com.gnagnoohc.scms.global.common.entity.BoardPost;
import com.gnagnoohc.scms.global.common.entity.BoardType;
import com.gnagnoohc.scms.global.common.entity.QBoardPost;
import com.gnagnoohc.scms.global.common.entity.QCommonCode;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.gnagnoohc.scms.global.common.entity.QBoardPost.boardPost;
import static com.gnagnoohc.scms.global.common.entity.QCommonCode.commonCode;

@RequiredArgsConstructor
public class BoardPostRepositoryImpl implements BoardPostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<BoardPost> search(BoardType boardType, String moduleCode, String categoryCode, String keyword,
                                   List<String> allowedStatuses, Pageable pageable) {
        QBoardPost post = boardPost;
        // FAQ_CATEGORY 그룹의 common_code와 category_code로 조인한다(FK 없는 값 기반 조인).
        // 정렬용 sort_order를 얻기 위함이며, FAQ가 아닐 때는 사용하지 않는다.
        QCommonCode category = commonCode;

        BooleanBuilder condition = new BooleanBuilder();
        condition.and(post.boardType.eq(boardType.name()));
        condition.and(post.deletedAt.isNull());
        condition.and(post.postStatus.in(allowedStatuses));
        if (boardType == BoardType.NOTICE && StringUtils.hasText(moduleCode)) {
            condition.and(post.moduleCode.eq(moduleCode));
        }
        if (boardType == BoardType.FAQ && StringUtils.hasText(categoryCode)) {
            condition.and(post.categoryCode.eq(categoryCode));
        }
        if (StringUtils.hasText(keyword)) {
            condition.and(post.title.containsIgnoreCase(keyword).or(post.content.containsIgnoreCase(keyword)));
        }

        JPAQuery<BoardPost> query = queryFactory
                .selectFrom(post)
                .leftJoin(post.authorUser).fetchJoin()
                .where(condition);

        if (boardType == BoardType.FAQ) {
            query.leftJoin(category)
                    .on(category.codeGroup.eq(BoardConstants.FAQ_CATEGORY_CODE_GROUP)
                            .and(category.code.eq(post.categoryCode)));
        }

        List<BoardPost> content = query
                .orderBy(resolveOrderSpecifiers(boardType, post, category))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory.select(post.count()).from(post).where(condition);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Optional<BoardPost> findDetail(Integer boardPostId) {
        QBoardPost post = boardPost;

        BoardPost result = queryFactory
                .selectFrom(post)
                .leftJoin(post.authorUser).fetchJoin()
                .where(post.boardPostId.eq(boardPostId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * NOTICE: 상단 고정 우선 → 등록일(createdAt) 내림차순(published_at 컬럼이 없어 등록일로 대체).
     * FAQ: 카테고리 sort_order(common_code) → 상단 고정 → 수정일 내림차순.
     * 마지막 boardPostId는 동률일 때 순서를 안정적으로 고정하기 위한 타이브레이커.
     */
    private OrderSpecifier<?>[] resolveOrderSpecifiers(BoardType boardType, QBoardPost post, QCommonCode category) {
        if (boardType == BoardType.FAQ) {
            return new OrderSpecifier<?>[] {
                    category.sortOrder.asc().nullsLast(),
                    post.pinned.desc(),
                    post.updatedAt.desc(),
                    post.boardPostId.asc()
            };
        }
        return new OrderSpecifier<?>[] {
                post.pinned.desc(),
                post.createdAt.desc(),
                post.boardPostId.desc()
        };
    }
}
