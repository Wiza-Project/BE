package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * NCS 직무 표준 데이터 접근 계층 (Repository)
 *
 * <p><strong>[설계 원칙 및 데이터 접근 기준]</strong></p>
 * <ul>
 *   <li>NCS 직무 표준 원장({@code ncs_standard})의 식별자 및 코드 기반 기본 조회를 수행한다.</li>
 *   <li>추후 pgvector 기반의 AI 잡매칭 유사도 쿼리 실행을 위한 확장 인터페이스로 활용된다.</li>
 * </ul>
 *
 * @author YUN
 */
public interface NcsStandardRepository extends JpaRepository<NcsStandard, Integer> {
}