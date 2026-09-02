package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSearchConditionDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.global.common.entity.QCommonCode;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

import static com.gnagnoohc.scms.domain.career.entity.QCompanyAccount.companyAccount;
import static com.gnagnoohc.scms.domain.career.entity.QJobPosting.jobPosting;
import static com.gnagnoohc.scms.global.common.entity.QCommonCode.commonCode;

/**
 * 채용공고 QueryDSL 동적 쿼리 커스텀 레포지토리 구현체
 *
 * <p><strong>[동적 쿼리 작성 및 패치 조인/프록시 메커니즘 설계 기준]</strong></p>
 * <p>본 구현체는 사용자 역할(학생/교직원)에 따른 데이터 접근 정책을 분리 적용하고,
 * 대용량 목록 조회 시 N+1 문제 방지 및 페이징 성능 최적화를 지원합니다.</p>
 *
 * <hr>
 * <h3>1. 연관 엔티티 패치 조인 (Fetch Join) & 객체 그래프 탐색</h3>
 * <ul>
 *   <li><b>N+1 문제 원천 차단:</b> 목록 출력 시 연관된 기업 정보({@code companyAccount}), 직무 공통코드({@code ncsCode}), 지역 공통코드({@code regionCode})를 단일 SELECT 문으로 패치 조인하여 가져옵니다.</li>
 *   <li><b>JPA 지연 로딩(LAZY) 및 프록시 대응:</b> 엔티티 기준 공통코드는 {@code FetchType.LAZY}로 설정되어 있어 DTO에서는 단순 식별자(ID)를 수신하더라도 엔티티 내부 필드는 {@code CommonCode} 프록시 객체로 매핑됩니다. 따라서 QueryDSL 조건절 비교 시 {@code jobPosting.ncsCode.codeId.eq(...)} 형태로 객체 그래프를 직접 탐색하여 비교 처리합니다.</li>
 * </ul>
 *
 * <hr>
 * <h3>2. 사용자 역할별 쿼리 분기 정책 (조회 vs 관리)</h3>
 * <ul>
 *   <li><b>학생용 공고 검색 ({@code searchStudentPostings}):</b>
 *     <ul>
 *       <li><b>비즈니스 목적:</b> 실제로 지원 가능한 유효 공고 탐색 및 채용 연계 신청</li>
 *       <li><b>기본 필수 제약 (WHERE 고정):</b> 게시 상태가 {@code PUBLISHED}(게시 완료)이면서 마감 일시가 {@code applicationEndsAt >= Instant.now()}(마감일 미경과)인 공고만 노출되도록 강제하여 미인가/마감 공고 노출을 원천 차단합니다.</li>
 *       <li><b>주요 검색 필터:</b> 직무({@code ncsCodeId}), 지역({@code regionCodeId}), 고용형태({@code employmentType}), 기업명({@code companyName}) 등 지원 조건 중심 필터링</li>
 *       <li><b>정렬 정책:</b> 지원 임박순({@code applicationEndsAt.asc()}) 우선 정렬 후 최신순 보조 정렬</li>
 *     </ul>
 *   </li>
 *   <li><b>교직원용 공고 검색 ({@code searchStaffPostings}):</b>
 *     <ul>
 *       <li><b>비즈니스 목적:</b> 기업 등록 공고의 검수(승인/반려), 수정, 삭제, 마감 여부 모니터링 등 운영·관리</li>
 *       <li><b>조회 정책:</b> 검수 대기({@code REQUESTED}), 승인({@code APPROVED}), 반려({@code REJECTED}), 마감({@code CLOSED}) 등 모든 상태의 이력을 모니터링해야 하므로 마감일/게시상태 고정 제약을 두지 않습니다.</li>
 *       <li><b>주요 검색 필터:</b> 검수 상태({@code reviewStatus}), 공고 구분({@code postingType}), 기업명({@code companyName}) 등 관리·승인 상태 중심 필터링</li>
 *       <li><b>정렬 정책:</b> 최근 접수된 검수 요청 건부터 신속히 처리하기 위해 최신 등록순({@code jobPostingId.desc()})으로 정렬</li>
 *       <li>기업 인증이 허가되지 않은 경우, 신규 공고 등록 시 목록에도 뜨지 않는다</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <hr>
 * <h3>3. 동적 조건 바인딩 및 카운트 쿼리 최적화</h3>
 * <ul>
 *   <li><b>동적 WHERE 절:</b> 검색 조건이 NULL이거나 공백 문자열인 경우 {@code BooleanExpression}에서 {@code null}을 반환하여 해당 검색 조건이 쿼리 생성 시 자동으로 무시되도록 처리합니다.</li>
 *   <li><b>카운트 쿼리 최적화:</b> {@code PageableExecutionUtils.getPage()}를 사용하여 첫 페이지에서 전체 데이터 개수가 페이지 크기보다 작거나 마지막 페이지인 경우 불필요한 COUNT 쿼리 실행을 방지합니다.</li>
 * </ul>
 *
 * @author YUN
 */
@RequiredArgsConstructor
public class JobPostingRepositoryImpl implements JobPostingRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QCommonCode ncsCommonCode = new QCommonCode("ncsCommonCode");
    private final QCommonCode regionCommonCode = new QCommonCode("regionCommonCode");

    @Override
    public Page<JobPosting> searchStudentPostings(JobPostingSearchConditionDTO cond, Pageable pageable) {
        List<JobPosting> content = queryFactory
                .selectFrom(jobPosting)
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .leftJoin(jobPosting.ncsCode, ncsCommonCode).fetchJoin()         // 공통코드 받는 - 엔티티에서 - ncsCode 필드 참조
                .leftJoin(jobPosting.regionCode, regionCommonCode).fetchJoin()   // 공통코드 받는 - 엔티티에서 - regionCode 필드 참조
                .where(
                        jobPosting.postingStatus.eq("PUBLISHED"),
                        jobPosting.applicationEndsAt.goe(Instant.now()),
                        ncsCodeIdEq(cond.getNcsCodeId()),
                        regionCodeIdEq(cond.getRegionCodeId()),
                        companyNameContains(cond.getCompanyName()),
                        employmentTypeEq(cond.getEmploymentType()),
                        postingTypeEq(cond.getPostingType())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(jobPosting.applicationEndsAt.asc(), jobPosting.jobPostingId.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(jobPosting.count())
                .from(jobPosting)
                .where(
                        jobPosting.postingStatus.eq("PUBLISHED"),
                        jobPosting.applicationEndsAt.goe(Instant.now()),
                        ncsCodeIdEq(cond.getNcsCodeId()),
                        regionCodeIdEq(cond.getRegionCodeId()),
                        companyNameContains(cond.getCompanyName()),
                        employmentTypeEq(cond.getEmploymentType()),
                        postingTypeEq(cond.getPostingType())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<JobPosting> searchStaffPostings(JobPostingSearchConditionDTO cond, Pageable pageable) {
        List<JobPosting> content = queryFactory
                .selectFrom(jobPosting)
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .leftJoin(jobPosting.ncsCode, ncsCommonCode).fetchJoin()         // ncsCode 필드 참조
                .leftJoin(jobPosting.regionCode, regionCommonCode).fetchJoin()   // regionCode 필드 참조
                .where(
                        reviewStatusEq(cond.getReviewStatus()),
                        postingTypeEq(cond.getPostingType()),
                        companyNameContains(cond.getCompanyName())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(jobPosting.jobPostingId.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(jobPosting.count())
                .from(jobPosting)
                .where(
                        reviewStatusEq(cond.getReviewStatus()),
                        postingTypeEq(cond.getPostingType()),
                        companyNameContains(cond.getCompanyName())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // --- 동적 조건 메서드 ---

    private BooleanExpression ncsCodeIdEq(Integer ncsCodeId) {
        // 엔티티의 ncsCode 객체 내부의 codeId 컬럼을 비교
        return ncsCodeId != null ? jobPosting.ncsCode.codeId.eq(ncsCodeId) : null;
    }

    private BooleanExpression regionCodeIdEq(Integer regionCodeId) {
        // 엔티티의 regionCode 객체 내부의 codeId 컬럼을 비교
        return regionCodeId != null ? jobPosting.regionCode.codeId.eq(regionCodeId) : null;
    }

    private BooleanExpression companyNameContains(String companyName) {
        return StringUtils.hasText(companyName) ? jobPosting.companyAccount.companyName.containsIgnoreCase(companyName) : null;
    }

    private BooleanExpression employmentTypeEq(String employmentType) {
        return StringUtils.hasText(employmentType) ? jobPosting.employmentType.eq(employmentType) : null;
    }

    private BooleanExpression postingTypeEq(String postingType) {
        return StringUtils.hasText(postingType) ? jobPosting.postingType.eq(postingType) : null;
    }

    private BooleanExpression reviewStatusEq(String reviewStatus) {
        return StringUtils.hasText(reviewStatus) ? jobPosting.reviewStatus.eq(reviewStatus) : null;
    }
}