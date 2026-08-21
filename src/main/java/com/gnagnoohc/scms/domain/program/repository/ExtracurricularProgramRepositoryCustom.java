package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExtracurricularProgramRepositoryCustom {

    // status가 null이면 상태 필터 없이 전체, keyword가 비어있으면 이름 검색 없이 전체를 조회한다.
    Page<ExtracurricularProgram> search(ProgramStatus status, String keyword, Pageable pageable);
}
