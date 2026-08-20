package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 상담 유형 엔티티의 영속성 조회를 담당한다.
 */
public interface CounselingTypeRepository extends JpaRepository<CounselingType, Integer> {

    /**
     * 신규 상담 신청 화면에는 현재 사용할 수 있는 유형만 안정된 코드 순서로 제공한다.
     */
    List<CounselingType> findAllByActiveTrueOrderByTypeCodeAsc();

    /**
     * 새 일정은 활성 유형으로만 만들되, 비활성화된 유형을 참조하는 과거 일정 자체는 보존한다.
     */
    Optional<CounselingType> findByCounselingTypeIdAndActiveTrue(Integer counselingTypeId);
}
