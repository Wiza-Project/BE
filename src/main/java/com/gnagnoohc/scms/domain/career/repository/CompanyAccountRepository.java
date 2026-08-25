package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 협약기업 엔티티 기본 Spring Data JPA Repository
 *
 * <p>기업 식별자의 무결성 보장을 위한 유니크 제약(사업자등록번호) 검증 쿼리 및
 * 커스텀 QueryDSL 인터페이스({@link CompanyAccountRepositoryCustom})를 상속하여 통합 제공</p>
 *
 * @author YUN
 */
public interface CompanyAccountRepository extends JpaRepository<CompanyAccount, Integer>, CompanyAccountRepositoryCustom {

    /**
     * 사업자등록번호 기준 기존 등록 기업 존재 여부 확인(필수)
     *
     * @param businessRegistrationNo 사업자등록번호 (10자리 또는 하이픈 포함 포맷)
     * @return 이미 등록된 기업이면 {@code true}, 신규 등록 가능하면 {@code false}
     */
    boolean existsByBusinessRegistrationNo(String businessRegistrationNo);

    /**
     * 사업자등록번호 기준으로 기업 단건 엔티티를 조회
     *
     * @param businessRegistrationNo 사업자등록번호
     * @return 조회된 협약기업 엔티티의 {@link Optional} 래퍼
     */
    Optional<CompanyAccount> findByBusinessRegistrationNo(String businessRegistrationNo);
}