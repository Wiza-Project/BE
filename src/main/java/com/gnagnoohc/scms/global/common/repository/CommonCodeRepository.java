package com.gnagnoohc.scms.global.common.repository;

import com.gnagnoohc.scms.global.common.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Integer> {
    List<CommonCode> findByCodeGroupAndActiveTrueOrderBySortOrderAsc(String codeGroup);

    /** 코드→한글명 단건 매핑용. CommonCodeService.getCodeName 참고. */
    Optional<CommonCode> findByCodeGroupAndCode(String codeGroup, String code);
}
