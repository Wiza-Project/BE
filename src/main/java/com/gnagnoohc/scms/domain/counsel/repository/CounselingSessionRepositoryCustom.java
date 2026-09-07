package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.projection.CounselingSessionRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * 회기 목록 조회의 동적 필터(sessionStatus/from/to) 전용 커스텀 레포지토리다.
 * JPQL의 {@code (:param is null or ...)} 방식은 PostgreSQL에서 타입 정보 없는 NULL 바인드의
 * 타입을 추론하지 못해 "could not determine data type of parameter" 오류(HTTP 500/C999)를 낸다.
 * 그래서 값이 있을 때만 조건을 붙이는 QueryDSL 동적 쿼리로 분리했다. 프로젝트의 다른 도메인
 * (career 등)이 이미 쓰는 커스텀 레포지토리 패턴과 동일하다.
 */
public interface CounselingSessionRepositoryCustom {

    /**
     * 로그인 상담사의 현재·과거 배정에 연결된 회기 목록. 선택 필터가 null이면 해당 조건을 붙이지 않는다.
     * 신청 원문·비공개 기록·공개 결과·연락처는 프로젝션에 포함하지 않는다.
     * careerOnly가 true면 ST300 단독(CAREER_ONLY) 상담사용으로 CS200(진로상담) 회기만 조회 조건에서 걸러낸다.
     */
    Page<CounselingSessionRow> findSessions(
            Integer counselorId,
            boolean careerOnly,
            String sessionStatus,
            Instant from,
            Instant to,
            Pageable pageable
    );
}
