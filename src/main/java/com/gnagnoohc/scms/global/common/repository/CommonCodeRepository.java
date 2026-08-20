package com.gnagnoohc.scms.global.common.repository;

import com.gnagnoohc.scms.global.common.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Integer> {
    List<CommonCode> findByCodeGroupAndActiveTrueOrderBySortOrderAsc(String codeGroup);
}
