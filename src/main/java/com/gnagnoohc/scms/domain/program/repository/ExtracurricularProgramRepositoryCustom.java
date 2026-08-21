package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ExtracurricularProgramRepositoryCustom {

    /**
     * status가 null이면 상태 필터 없이 전체, keyword가 비어있으면 이름 검색 없이 전체,
     * competencyId가 null이면 연계 핵심역량 필터 없이 전체를 조회한다.
     */
    Page<ExtracurricularProgram> search(ProgramStatus status, String keyword, Integer competencyId, Pageable pageable);

    /**
     * staff 목록 조회용. search()와 동일한 필터에 managerUserId(본인 담당) 조건만 추가한다.
     */
    Page<ExtracurricularProgram> searchByManager(Integer managerUserId, ProgramStatus status, String keyword,
                                                  Integer competencyId, Pageable pageable);

    /**
     * 상세 조회용. 화면에 필요한 모든 *-to-one 연관관계를 fetch join으로 한 번에 가져온다.
     */
    Optional<ExtracurricularProgram> findDetailById(Integer programId);
}
