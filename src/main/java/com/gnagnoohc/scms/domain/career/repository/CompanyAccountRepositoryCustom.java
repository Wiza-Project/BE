package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.dto.company.CompanySearchConditionDTO;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 협약기업 엔티티 QueryDSL 동적 쿼리 인터페이스
 *
 * <p>교직원 및 관리자 화면에서 요청하는 기업 목록 다중 조건 검색 및 페이징 쿼리 명세를 정의 용도</p>
 *
 * @author YUN
 */
public interface CompanyAccountRepositoryCustom {

    /**
     * 다중 필터 조건(기업명, 사업자번호, 검증상태, 계정상태)을 기반으로 기업 목록을 동적 조회
     *
     * @param cond     기업 다중 검색 조건 DTO (nullable 파라미터는 조건 절에서 자동 제외)
     * @param pageable 페이징 파라미터 (정렬, 오프셋, 페이지 크기)
     * @return 검색 조건에 부합하는 기업 엔티티의 페이징 객체
     */
    Page<CompanyAccount> searchCompanies(CompanySearchConditionDTO cond, Pageable pageable);
}