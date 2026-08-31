package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.dto.company.CompanySearchConditionDTO;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.gnagnoohc.scms.domain.career.entity.QCompanyAccount.companyAccount;

/**
 * 협약기업 엔티티 QueryDSL 동적 쿼리 구현체
 *
 * <p>{@link JPAQueryFactory}를 활용하여 Null-safe한 동적 Where 조건을 결합하고,
 * 대용량 데이터 환경을 고려하여 Content 쿼리와 Count 쿼리를 분리 실행하는 용도</p>
 *
 * QueryDSL BooleanExpression 분리 로직
 * JPAQueryFactory.where(...)로 인자로 전달된 null 값을 조건절에서 자동으로 제외하는 자동 Null-Safe 처리 구현
 * 각 검색 필터 조건을 독립된 BooleanExpression 메서드로 분리하여 코드의 의도가 명확 & 다른 쿼리에서도 재사용할 수 있도록 조건의 모듈화 및 재사용성 감안하여 설계
 *
 * @author YUN
 */
@RequiredArgsConstructor
public class CompanyAccountRepositoryImpl implements CompanyAccountRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * {@inheritDoc}
     * <p>성능 최적화를 위해 Content 조회와 Total Count 조회를 분리 실행합니다.</p>
     */
    @Override
    public Page<CompanyAccount> searchCompanies(CompanySearchConditionDTO cond, Pageable pageable) {
        List<CompanyAccount> content = queryFactory
                .selectFrom(companyAccount)
                .where(
                        companyNameContains(cond.getCompanyName()),
                        businessRegNoEq(cond.getBusinessRegistrationNo()),
                        verificationStatusEq(cond.getVerificationStatus()),
                        accountStatusEq(cond.getAccountStatus())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(companyAccount.companyAccountId.desc())
                .fetch();

        Long total = queryFactory
                .select(companyAccount.count())
                .from(companyAccount)
                .where(
                        companyNameContains(cond.getCompanyName()),
                        businessRegNoEq(cond.getBusinessRegistrationNo()),
                        verificationStatusEq(cond.getVerificationStatus()),
                        accountStatusEq(cond.getAccountStatus())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /**
     * 기업명 부분 일치 검색 조건 (대소문자 무시 LIKE '%keyword%')
     */
    private BooleanExpression companyNameContains(String companyName) {
        return StringUtils.hasText(companyName) ? companyAccount.companyName.containsIgnoreCase(companyName) : null;
    }

    /**
     * 사업자등록번호 일치 검색 조건 (EQ)
     */
    private BooleanExpression businessRegNoEq(String businessRegNo) {
        return StringUtils.hasText(businessRegNo) ? companyAccount.businessRegistrationNo.eq(businessRegNo) : null;
    }

    /**
     * 기업 승인/검증 상태 일치 검색 조건 (EQ)
     */
    private BooleanExpression verificationStatusEq(String verificationStatus) {
        return StringUtils.hasText(verificationStatus) ? companyAccount.verificationStatus.eq(verificationStatus) : null;
    }

    /**
     * 계정 활성화 상태 일치 검색 조건 (EQ)
     */
    private BooleanExpression accountStatusEq(String accountStatus) {
        return StringUtils.hasText(accountStatus) ? companyAccount.accountStatus.eq(accountStatus) : null;
    }
}