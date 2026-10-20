package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.StudentJobRelation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * 학생-채용공고 관계(지원/스크랩/잡매칭) 기본 JPA Repository
 *
 * <p><strong>[가이드라인 및 아키텍처 원칙]</strong></p>
 * <ul>
 *   <li>기본 단건 조회 및 존재 여부 확인은 Spring Data JPA의 파생 쿼리 메서드(Derived Query Method)로 처리</li>
 *   <li>다대일 관계(AppUser, JobPosting, CompanyAccount)의 N+1 방지 Fetch Join 및 동적 쿼리는 Custom/Impl로 위임</li>
 * </ul>
 *
 * @author YUN
 */
public interface StudentJobRelationRepository extends JpaRepository<StudentJobRelation, Integer>, StudentJobRelationRepositoryCustom {

    /**
     * [단건 조회] 학생 PK와 채용공고 PK로 관계 엔티티 조회
     *
     * <p><strong>[사용 용도]</strong></p>
     * <ul>
     *   <li>중복 지원 여부 검증</li>
     *   <li>스크랩(북마크) 토글 시 기존 관계 엔티티 존재 유무 확인</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param jobPostingId  채용공고 식별자 (job_posting.job_posting_id)
     * @return StudentJobRelation 엔티티 Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentJobRelation> findByStudent_UserIdAndJobPosting_JobPostingId(Integer studentUserId, Integer jobPostingId);

    /**
     * [중복 지원 방지] 학생이 해당 공고에 이미 유효하게 지원 중인지 확인
     *
     * <p><strong>[검증 조건]</strong></p>
     * <ul>
     *   <li>{@code applied_at IS NOT NULL} (지원 완료) AND {@code canceled_at IS NULL} (미취소 상태)</li>
     *   <li>지원 취소 후 재지원인 경우에는 false를 반환하여 재지원 허용</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param jobPostingId  채용공고 식별자 (job_posting.job_posting_id)
     * @return true: 현재 유효 지원 중, false: 미지원 또는 취소 완료 상태
     */
    boolean existsByStudent_UserIdAndJobPosting_JobPostingIdAndAppliedAtIsNotNullAndCanceledAtIsNull(Integer studentUserId, Integer jobPostingId);
}