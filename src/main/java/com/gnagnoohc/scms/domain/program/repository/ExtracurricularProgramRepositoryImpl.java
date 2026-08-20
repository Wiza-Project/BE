package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.entity.QExtracurricularProgram;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.gnagnoohc.scms.domain.program.entity.QExtracurricularProgram.extracurricularProgram;

@RequiredArgsConstructor
public class ExtracurricularProgramRepositoryImpl implements ExtracurricularProgramRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ExtracurricularProgram> search(ProgramStatus status, String keyword, Pageable pageable) {
        return search(null, status, keyword, pageable);
    }

    @Override
    public Page<ExtracurricularProgram> searchByManager(Integer managerUserId, ProgramStatus status, String keyword,
                                                          Pageable pageable) {
        return search(managerUserId, status, keyword, pageable);
    }

    private Page<ExtracurricularProgram> search(Integer managerUserId, ProgramStatus status, String keyword,
                                                  Pageable pageable) {
        QExtracurricularProgram program = extracurricularProgram;

        BooleanBuilder condition = new BooleanBuilder();
        if (managerUserId != null) {
            condition.and(program.managerUser.userId.eq(managerUserId));
        }
        if (status != null) {
            condition.and(program.programStatus.eq(status));
        }
        if (StringUtils.hasText(keyword)) {
            condition.and(program.programName.containsIgnoreCase(keyword));
        }

        // operatingUnitCode/programTypeCode/competency는 목록 DTO 매핑에서 라벨(codeName/competencyName)로
        // 바로 쓰이므로, LAZY 연관관계를 지연 로딩(N+1)하지 않고 fetch join으로 한 번에 가져온다.
        List<ExtracurricularProgram> content = queryFactory
                .selectFrom(program)
                .leftJoin(program.operatingUnitCode).fetchJoin()
                .leftJoin(program.programTypeCode).fetchJoin()
                .leftJoin(program.competency).fetchJoin()
                .where(condition)
                .orderBy(resolveOrderSpecifiers(pageable.getSort(), program))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(program.count())
                .from(program)
                .where(condition);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Optional<ExtracurricularProgram> findDetailById(Integer programId) {
        QExtracurricularProgram program = extracurricularProgram;

        // 상세 화면에 필요한 모든 *-to-one 연관관계를 한 번에 fetch join한다.
        // 전부 ManyToOne(단일 값) 관계라 컬렉션 페치조인처럼 카티전 곱이 발생하지 않는다.
        ExtracurricularProgram result = queryFactory
                .selectFrom(program)
                .leftJoin(program.operatingUnitCode).fetchJoin()
                .leftJoin(program.programTypeCode).fetchJoin()
                .leftJoin(program.competency).fetchJoin()
                .leftJoin(program.managerUser).fetchJoin()
                .leftJoin(program.mileagePolicy).fetchJoin()
                .leftJoin(program.fileGroup).fetchJoin()
                .where(program.programId.eq(programId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    // 클라이언트가 보낸 정렬 프로퍼티를 임의로 신뢰하면 존재하지 않는 필드로 쿼리가 깨질 수 있으므로,
    // 허용된 필드만 화이트리스트로 매핑한다. 매칭되는 정렬 키가 하나도 없으면 등록일 내림차순을 기본값으로 쓴다.
    private OrderSpecifier<?>[] resolveOrderSpecifiers(Sort sort, QExtracurricularProgram program) {
        List<OrderSpecifier<?>> orders = sort.stream()
                .<OrderSpecifier<?>>map(order -> toOrderSpecifier(order, program))
                .filter(java.util.Objects::nonNull)
                .toList();

        if (orders.isEmpty()) {
            return new OrderSpecifier<?>[] { program.createdAt.desc() };
        }
        return orders.toArray(OrderSpecifier<?>[]::new);
    }

    private OrderSpecifier<?> toOrderSpecifier(Sort.Order order, QExtracurricularProgram program) {
        ComparableExpressionBase<?> path = switch (order.getProperty()) {
            case "createdAt" -> program.createdAt;
            case "recruitmentEndsAt" -> program.recruitmentEndsAt;
            case "programName" -> program.programName;
            default -> null;
        };
        if (path == null) {
            return null;
        }
        return order.isAscending() ? path.asc() : path.desc();
    }
}
