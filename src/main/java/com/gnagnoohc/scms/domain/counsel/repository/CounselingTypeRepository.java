package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 상담 유형 엔티티의 영속성 조회를 담당한다.
 */
public interface CounselingTypeRepository extends JpaRepository<CounselingType, Integer> {

    /**
     * Spring Data JPA가 메서드명의 {@code ActiveTrue}를 {@code active = true} 조건으로 해석하고,
     * {@code OrderByTypeCodeAsc}를 {@code typeCode} 오름차순 정렬로 해석해 쿼리를 생성한다.
     */
    List<CounselingType> findAllByActiveTrueOrderByTypeCodeAsc();
}
