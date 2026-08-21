package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.StudentJobRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * StudentJobRelation QueryDSL 동적 쿼리 및 성능 최적화 전용 인터페이스
 *
 * <p><strong>[가이드라인 및 아키텍처 원칙]</strong></p>
 * <ul>
 *   <li>open-in-view: false 환경에서 DTO 변환 시 지연로딩(LAZY) N+1 방지를 위해 Fetch Join 필수 선언</li>
 *   <li>동적 조건(전형 상태 필터링) 및 PageableExecutionUtils 카운트 쿼리 최적화 지원</li>
 * </ul>
 *
 * @author YUN
 */
public interface StudentJobRelationRepositoryCustom {

    /**
     * <p>[학생용] 내가 스크랩한 관심 공고 목록 페이징 조회</p>
     * <ul>
     *   <li>기능: bookmarked_at IS NOT NULL 기준 공고 마감 임박순(applicationEndsAt ASC) 정렬 페이징</li>
     *   <li>성능: JobPosting, CompanyAccount Fetch Join으로 N+1 방지</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param pageable      페이징 및 슬라이스 정보
     * @return 스크랩 관계 엔티티 페이징 객체
     */
    Page<StudentJobRelation> findScrappedPostingsByStudent(Integer studentUserId, Pageable pageable);

    /**
     * <p>[학생용] 내 채용 지원 이력 및 전형 상태 목록 페이징 조회</p>
     * <ul>
     *   <li>기능: applied_at IS NOT NULL 기준 최신 지원일시순(appliedAt DESC) 정렬 페이징</li>
     *   <li>성능: JobPosting, CompanyAccount Fetch Join으로 N+1 방지</li>
     * </ul>
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param pageable      페이징 및 슬라이스 정보
     * @return 지원 이력 관계 엔티티 페이징 객체
     */
    Page<StudentJobRelation> findApplicationsByStudent(Integer studentUserId, Pageable pageable);

    /**
     * <p>[교직원용] 특정 채용공고의 지원자/추천 학생 전체 목록 페이징 조회</p>
     * <ul>
     *   <li>기능: 전형 진행 상태(applicationStatus) 동적 다중 필터링 및 선착순/지원일시순(appliedAt ASC) 정렬</li>
     *   <li>성능: AppUser, JobPosting, CompanyAccount 3단 Fetch Join으로 지원자 목록 렌더링 시 N+1 원천 차단</li>
     * </ul>
     *
     * @param jobPostingId      채용공고 식별자 (job_posting.job_posting_id)
     * @param applicationStatus 전형 상태 필터 (APPLIED, DOCUMENT_PASS, FINAL_PASS 등, 미선택 시 전체)
     * @param pageable          페이징 및 정렬 정보
     * @return 지원자 관계 엔티티 페이징 객체
     */
    Page<StudentJobRelation> findApplicantsByJobPosting(Integer jobPostingId, String applicationStatus, Pageable pageable);

    /**
     * <p>[공통] 관계 식별자 단건 상세 조회</p>
     * <ul>
     *  <li>기능: 단건 데이터 조회 시 연관 엔티티(AppUser, JobPosting, CompanyAccount) 일괄 즉시 로딩</li>
     * </ul>
     * @param relationId 관계 식별자 (student_job_relation_id PK)
     * @return 연관 객체가 완벽히 패치된 StudentJobRelation Optional
     */
    Optional<StudentJobRelation> findByIdWithDetails(Integer relationId);
}