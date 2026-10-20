package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSearchConditionDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 채용공고 QueryDSL 동적 쿼리 전용 커스텀 인터페이스
 *
 * <p><strong>[사용자 역할별 쿼리 메서드 분리 배경 및 설계 기준]</strong></p>
 * <p>학생과 교직원은 서비스 이용 목적, 데이터 접근 권한, 정렬 기준이 완전히 다르므로 단일 메서드로 통합하지 않고 명확히 분리
 * 화면 파라미터 조작을 통한 비인가 데이터 누출(미승인/마감 공고 열람)을 레포지토리 레벨에서 원천 차단</p>
 *
 * <hr>
 * <h3>1. 학생용 공고 검색 ({@code searchStudentPostings})</h3>
 * <ul>
 *   <li><b>비즈니스 목적:</b> 실제로 지원 가능한 유효 공고 탐색 및 채용 연계 신청</li>
 *   <li><b>기본 필수 제약 (WHERE 고정):</b>
 *     <ul>
 *       <li>게시 상태: {@code postingStatus = 'PUBLISHED'} (최종 게시 승인 완료된 공고만 노출)</li>
 *       <li>마감 기한: {@code applicationEndsAt >= Instant.now()} (현재 시점 기준 마감되지 않은 공고만 노출)</li>
 *     </ul>
 *   </li>
 *   <li><b>주요 검색 필터:</b> 직무({@code ncsCodeId}), 지역({@code regionCodeId}), 고용형태({@code employmentType}), 기업명({@code companyName}) 등 지원자 맞춤 탐색 조건</li>
 *   <li><b>정렬 정책:</b> 마감 임박순({@code applicationEndsAt.asc()}) 우선 정렬을 통해 빠른 지원 유도 후 최신순 보조 정렬</li>
 * </ul>
 *
 * <hr>
 * <h3>2. 교직원용 공고 검색 ({@code searchStaffPostings})</h3>
 * <ul>
 *   <li><b>비즈니스 목적:</b> 기업 등록 공고의 검수(승인/반려), 수정, 삭제 및 전체 채용 라이프사이클 운영·관리</li>
 *   <li><b>조회 정책:</b> 검수 대기({@code REQUESTED}), 반려({@code REJECTED}), 마감({@code CLOSED}) 등 모든 상태의 이력을 모니터링해야 하므로 마감일/게시상태 고정 제약을 두지 않음</li>
 *   <li><b>주요 검색 필터:</b> 검수 상태({@code reviewStatus}), 공고 구분({@code postingType}), 기업명({@code companyName}) 등 관리·승인 업무 중심 필터</li>
 *   <li><b>정렬 정책:</b> 최신 등록순({@code jobPostingId.desc()})으로 정렬하여 최근 접수된 검수 요청 건부터 신속히 처리</li>
 * </ul>
 *
 * <hr>
 * <h3>3. 페이징 및 성능 최적화 공통 기준</h3>
 * <ul>
 *   <li>{@code Pageable} 기반의 Offset 페이징 적용</li>
 *   <li>{@code PageableExecutionUtils}를 적용하여 첫 페이지 데이터가 페이지 크기보다 작거나 마지막 페이지인 경우 불필요한 COUNT 쿼리 실행을 방지</li>
 * </ul>
 *
 * @author YUN
 */
public interface JobPostingRepositoryCustom {

    /**
     * [학생용] 동적 다중 조건 필터 검색 (게시 상태 PUBLISHED + 마감일 미경과 로직 필수)
     */
    Page<JobPosting> searchStudentPostings(JobPostingSearchConditionDTO cond, Pageable pageable);

    /**
     * [교직원용] 동적 다중 조건 필터 검색 (전체 상태 및 조건 조회)
     */
    Page<JobPosting> searchStaffPostings(JobPostingSearchConditionDTO cond, Pageable pageable);
}