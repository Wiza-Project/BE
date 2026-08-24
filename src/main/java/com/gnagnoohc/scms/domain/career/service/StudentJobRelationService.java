package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.relation.JobRelationRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.relation.JobRelationResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.relation.JobScrapSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.relation.JobScrapToggleResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.entity.StudentJobRelation;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.career.repository.StudentJobRelationRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.helper.JdbcUpsertHelper;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 학생-채용공고 관계(온라인 지원, 관심 공고 스크랩, 전형 현황) 비즈니스 로직 서비스
 *
 * <p><strong>[가이드라인 및 아키텍처 원칙]</strong></p>
 * <ul>
 *   <li><b>상태 제어:</b> 엔티티의 불변성을 유지하고 비즈니스 메서드({@code apply()}, {@code cancelApplication()}, {@code toggleBookmark()})를 통해 상태 전이 수행</li>
 *   <li><b>보안 및 유효성 검증:</b> 공고의 게시 상태({@code PUBLISHED}), 접수 마감 기한, 중복 지원 여부를 트랜잭션 내에서 철저히 검증</li>
 *   <li><b>시간 표준화:</b> DB의 UTC/TIMESTAMPTZ 일시({@link Instant})를 한국 표준시 KST({@link OffsetDateTime})로 변환하여 DTO 매핑</li>
 * </ul>
 *
 * @author YUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentJobRelationService {

    private final StudentJobRelationRepository relationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AppUserRepository appUserRepository;
    private final JdbcUpsertHelper jdbcUpsertHelper;

    /**
     * [학생] 온라인 채용공고 지원 신청
     *
     * <p><strong>[비즈니스 로직 처리 순서]</strong></p>
     * <ul>
     *   <li>1. 공고 및 학생 사용자 유효성 검증</li>
     *   <li>2. 공고 게시 상태({@code PUBLISHED}) 및 접수 마감 기한 초과 여부 검증</li>
     *   <li>3. 기존 유효 지원 건에 대한 중복 지원 방지 검증</li>
     *   <li>4. 관계 엔티티 조회 또는 신규 생성 후 {@code apply()} 비즈니스 메서드 호출</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param requestDTO    지원 요청 DTO
     * @return 지원 완료 상세 및 전형 상태 응답 DTO
     */
    @Transactional
    public JobRelationResponseDTO applyJob(Integer studentUserId, JobRelationRequestDTO requestDTO) {
        JobPosting jobPosting = jobPostingRepository.findById(requestDTO.getJobPostingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        AppUser student = appUserRepository.findById(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!"PUBLISHED".equalsIgnoreCase(jobPosting.getPostingStatus())) {
            throw new BusinessException(ErrorCode.JOB_POSTING_NOT_PUBLISHED);
        }
        if (jobPosting.getApplicationEndsAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.APPLICATION_PERIOD_EXPIRED);
        }

        // 1. 관계 Row 원자적 확보 (없으면 ON CONFLICT DO NOTHING 삽입 후 조회)
        StudentJobRelation relation = getOrCreateRelation(student, jobPosting);

        // 2. 이미 활성 지원 상태인 경우에만 중복 지원 예외 발생
        if (relation.getAppliedAt() != null && relation.getCanceledAt() == null) {
            throw new BusinessException(ErrorCode.JOB_POSTING_ALREADY_APPLIED);
        }

        relation.apply(null, "STUDENT_DIRECT");

        return mapToRelationResponseDTO(relation);
    }

    /**
     * [학생] 온라인 채용공고 지원 취소 처리
     *
     * <p><strong>[비즈니스 로직 처리 순서]</strong></p>
     * <ul>
     *   <li>1. 공고 접수 마감 기한 경과 여부 검증 (마감 이후 취소 불가)</li>
     *   <li>2. 지원 이력 존재 및 취소 가능 상태 여부 검증</li>
     *   <li>3. {@code cancelApplication()} 메서드를 통해 취소 일시({@code canceled_at}) 기록 및 상태 {@code CANCELED} 전환</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param jobPostingId  채용공고 식별자 (job_posting.job_posting_id)
     */
    @Transactional
    public void cancelApplication(Integer studentUserId, Integer jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        if (jobPosting.getApplicationEndsAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.APPLICATION_PERIOD_EXPIRED);
        }

        StudentJobRelation relation = relationRepository
                .findByStudent_UserIdAndJobPosting_JobPostingId(studentUserId, jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_APPLICATION_NOT_FOUND));

        if (relation.getAppliedAt() == null || relation.getCanceledAt() != null) {
            throw new BusinessException(ErrorCode.JOB_POSTING_APPLICATION_NOT_FOUND);
        }

        relation.cancelApplication();
    }

    /**
     * [학생] 관심 공고 스크랩 토글 (북마크 등록 및 해제)
     *
     * <p><strong>[비즈니스 로직 처리 순서]</strong></p>
     * <ul>
     *   <li>1. 공고 및 학생 유효성 검증</li>
     *   <li>2. 관계 엔티티 조회 또는 신규 생성 후 {@code toggleBookmark()} 호출</li>
     *   <li>3. 북마크 일시의 존재 여부에 따라 가상 플래그 {@code isScrapped}를 조립하여 반환</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param jobPostingId  채용공고 식별자 (job_posting.job_posting_id)
     * @return 스크랩 토글 결과 응답 DTO
     */
    @Transactional
    public JobScrapToggleResponseDTO toggleScrap(Integer studentUserId, Integer jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        AppUser student = appUserRepository.findById(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        StudentJobRelation relation = getOrCreateRelation(student, jobPosting);

        boolean isScrapped = relation.toggleBookmark();

        return JobScrapToggleResponseDTO.builder()
                .jobPostingId(jobPosting.getJobPostingId())
                .isScrapped(isScrapped)
                .bookmarkedAt(DateTimeUtils.toKstOffsetDateTime(relation.getBookmarkedAt()))
                .build();
    }

    /**
     * [학생] 내 관심 공고 스크랩 목록 페이징 조회
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param pageable      페이징 파라미터
     * @return 스크랩 공고 요약 목록 페이징 응답 DTO
     */
    public PageResponse<JobScrapSummaryResponseDTO> getMyScrappedPostings(Integer studentUserId, Pageable pageable) {
        Page<StudentJobRelation> page = relationRepository.findScrappedPostingsByStudent(studentUserId, pageable);
        Page<JobScrapSummaryResponseDTO> dtoPage = page.map(this::mapToScrapSummaryResponseDTO);
        return PageResponse.from(dtoPage);
    }

    /**
     * [학생] 내 온라인 지원 내역 및 전형 상태 페이징 조회
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param pageable      페이징 파라미터
     * @return 지원 내역 상세 페이징 응답 DTO
     */
    public PageResponse<JobRelationResponseDTO> getMyApplications(Integer studentUserId, Pageable pageable) {
        Page<StudentJobRelation> page = relationRepository.findApplicationsByStudent(studentUserId, pageable);
        Page<JobRelationResponseDTO> dtoPage = page.map(this::mapToRelationResponseDTO);
        return PageResponse.from(dtoPage);
    }

    /**
     * [교직원/관리자] 특정 공고별 지원자 목록 및 전형 관리 페이징 조회
     *
     * @param jobPostingId      채용공고 식별자
     * @param applicationStatus 전형 상태 필터 조건 (null 허용)
     * @param pageable          페이징 파라미터
     * @return 지원자 목록 페이징 응답 DTO
     */
    public PageResponse<JobRelationResponseDTO> getApplicantsByJobPosting(Integer jobPostingId, String applicationStatus, Pageable pageable) {
        Page<StudentJobRelation> page = relationRepository.findApplicantsByJobPosting(jobPostingId, applicationStatus, pageable);
        Page<JobRelationResponseDTO> dtoPage = page.map(this::mapToRelationResponseDTO);
        return PageResponse.from(dtoPage);
    }

    /**
     * 학생-채용공고 관계 엔티티를 클라이언트 반환용 응답 DTO로 매핑 변환
     * 시간 데이터는 공통 시간 유틸리티({@link DateTimeUtils})지정한 KST 오프셋 메소드를 호출-변환 처리
     *
     * @param relation 학생-공고 관계 엔티티 원장
     * @return 상세 지원 현황 및 전형 응답 DTO
     */
    private JobRelationResponseDTO mapToRelationResponseDTO(StudentJobRelation relation) {
        return JobRelationResponseDTO.builder()
                .studentJobRelationId(relation.getStudentJobRelationId())
                .jobPostingId(relation.getJobPosting().getJobPostingId())
                .postingTitle(relation.getJobPosting().getPostingTitle())
                .companyName(relation.getJobPosting().getCompanyAccount().getCompanyName())
                .postingType(relation.getJobPosting().getPostingType())
                .userId(relation.getStudent().getUserId())
                .universityNo(relation.getStudent().getUniversityNo())
                .userName(relation.getStudent().getUserName())
                .applicationStatus(relation.getApplicationStatus())
                .selectionStage(relation.getSelectionStage())
                .selectionResult(relation.getSelectionResult())
                .recommendationSource(relation.getRecommendationSource())
                .matchingScore(relation.getMatchingScore())
                .userConsentId(relation.getUserConsent() != null ? relation.getUserConsent().getUserConsentId() : null)
                .appliedAt(DateTimeUtils.toKstOffsetDateTime(relation.getAppliedAt()))
                .canceledAt(DateTimeUtils.toKstOffsetDateTime(relation.getCanceledAt()))
                .build();
    }

    /**
     * 학생-채용공고 관계 엔티티를 클라이언트 스크랩 목록 요약 응답 DTO로 매핑 변환
     * 시간 데이터는 공통 시간 유틸리티({@link DateTimeUtils})지정한 KST 오프셋 메소드를 호출-변환 처리
     *
     * @param relation 학생-공고 관계 엔티티 원장
     * @return 스크랩 목록 요약 응답 DTO
     */
    private JobScrapSummaryResponseDTO mapToScrapSummaryResponseDTO(StudentJobRelation relation) {
        return JobScrapSummaryResponseDTO.builder()
                .studentJobRelationId(relation.getStudentJobRelationId())
                .jobPostingId(relation.getJobPosting().getJobPostingId())
                .companyName(relation.getJobPosting().getCompanyAccount().getCompanyName())
                .postingTitle(relation.getJobPosting().getPostingTitle())
                .ncsCodeName(relation.getJobPosting().getNcsCode() != null ? relation.getJobPosting().getNcsCode().getCodeName() : null)
                .regionCodeName(relation.getJobPosting().getRegionCode() != null ? relation.getJobPosting().getRegionCode().getCodeName() : null)
                .employmentType(relation.getJobPosting().getEmploymentType())
                .postingType(relation.getJobPosting().getPostingType())
                .benefitType(relation.getJobPosting().getBenefitType())
                .applicationEndsAt(DateTimeUtils.toKstOffsetDateTime(relation.getJobPosting().getApplicationEndsAt()))
                .bookmarkedAt(DateTimeUtils.toKstOffsetDateTime(relation.getBookmarkedAt()))
                .build();
    }

    /**
     * PostgreSQL ON CONFLICT DO NOTHING을 활용한 원자적 관계 엔티티 확보 헬퍼
     *
     * @param student    학생 사용자 엔티티
     * @param jobPosting 채용공고 엔티티
     * @return 확보된 학생-채용공고 관계 영속 엔티티
     */
    private StudentJobRelation getOrCreateRelation(AppUser student, JobPosting jobPosting) {
        Instant now = Instant.now();
        String sql = "INSERT INTO student_job_relation (student_id, job_posting_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (student_id, job_posting_id) DO NOTHING";

        jdbcUpsertHelper.executeInsertDoNothing(
                sql,
                student.getUserId(),
                jobPosting.getJobPostingId(),
                now,
                now
        );

        return relationRepository.findByStudent_UserIdAndJobPosting_JobPostingId(student.getUserId(), jobPosting.getJobPostingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "공고 관계 정보를 처리할 수 없습니다."));
    }

}