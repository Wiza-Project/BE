package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.career.dto.posting.*;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.repository.CompanyAccountRepository;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.common.service.CommonCodeService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 채용공고 핵심 비즈니스 로직 서비스
 *
 * <p><strong>[설계 원칙 및 사용자 역할별 라이프사이클 관리 기준]</strong></p>
 * <p>본 서비스는 학생의 채용공고 탐색 및 교직원의 구인 신청 검수/게시 라이프사이클을 총괄하며,
 * 비즈니스 정합성 검증, DTO-Entity 간 데이터 바인딩, JSON/시간대 변환을 전담합니다.</p>
 *
 * <hr>
 * <h3>1. 사용자 역할별 접근 및 조회 분기 정책</h3>
 * <ul>
 *   <li><b>학생용 공고 탐색 ({@code getStudentJobPostings}):</b>
 *     <ul>
 *       <li>게시 완료({@code PUBLISHED}) 및 접수 마감 미경과({@code applicationEndsAt >= NOW}) 공고만 노출 강제</li>
 *       <li>마감 임박순 정렬 기반 페이징 요약 DTO({@code JobPostingSummaryResponseDTO}) 반환</li>
 *     </ul>
 *   </li>
 *   <li><b>교직원용 공고 모니터링 ({@code getStaffJobPostings}):</b>
 *     <ul>
 *       <li>검수 대기({@code REQUESTED}), 반려({@code REJECTED}), 승인/게시({@code PUBLISHED}), 마감({@code CLOSED}) 등 전 상태 이력 조회</li>
 *       <li>최신 등록순 정렬 기반 페이징 요약 DTO 반환</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <hr>
 * <h3>2. 검수(승인/반려) 라이프사이클 및 도메인 캡슐화</h3>
 * <ul>
 *   <li><b>승인 처리 ({@code APPROVED}):</b> 엔티티의 {@code review()} 도메인 메서드를 통해 게시 상태({@code PUBLISHED}), 공개일시({@code publishedAt}), 검수자 ID({@code reviewedBy}), 검토일시({@code reviewedAt})를 원자적으로 갱신</li>
 *   <li><b>반려 처리 ({@code REJECTED}):</b> 반려 사유({@code rejectionReason}) 입력 여부를 필수로 검증(누락 시 {@code INVALID_INPUT} 발생) 후 임시 상태({@code DRAFT})로 유지</li>
 *   <li><b>검토자 보안:</b> 클라이언트 조작 방지를 위해 검수자 식별자는 DTO가 아닌 Controller의 Security 인증 컨텍스트에서 직접 주입</li>
 * </ul>
 *
 * <hr>
 * <h3>3. 데이터 정합성 검증 및 객체 변환 최적화</h3>
 * <ul>
 *   <li><b>기간 유효성 검증:</b> 공고 등록/수정 시 접수 시작일시가 종료일시보다 늦은 역전 현상 방지 (위반 시 {@code INVALID_APPLICATION_PERIOD} 발생)</li>
 *   <li><b>JSONB 타입 매핑:</b> 프론트엔드 통신용 {@code Map<String, Object>}과 DB PostgreSQL JSONB용 {@code JsonNode} 간 상호 변환을 내부 {@code ObjectMapper}로 안전하게 처리</li>
 *   <li><b>시간대 표준화:</b> DB 영속화 기준 UTC {@code Instant}와 API 응답 기준 KST (Asia/Seoul) {@code OffsetDateTime} 간 표준 변환 지원</li>
 * </ul>
 *
 * @author YUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final CompanyAccountRepository companyAccountRepository;
    private final CommonCodeRepository commonCodeRepository;
    // 공통코드 매핑용 서비스 주입
    private final CommonCodeService commonCodeService;
    // 사용자 정보 조회용 공통 AppUser 추가 (교직원 중에서도 취창업 부서 검증용)
    private final AppUserRepository appUserRepository;

    // 빈 주입 의존성 경고 방지 및 독립적 JSON 직렬화/역직렬화를 위한 객체 생성
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [학생용] 채용공고 다중 조건 검색 및 목록 페이징 조회
     *
     * @param cond 검색 필터 파라미터 DTO (직무, 지역, 기업명, 고용형태, 공고구분 등)
     * @param pageable 페이징 파라미터
     * @return 채용공고 목록 요약 응답 DTO 페이징 객체
     */
    public Page<JobPostingSummaryResponseDTO> getStudentJobPostings(JobPostingSearchConditionDTO cond, Pageable pageable) {
        Page<JobPosting> postingPage = jobPostingRepository.searchStudentPostings(cond, pageable);
        return postingPage.map(this::convertToSummaryDTO);
    }

    /**
     * [교직원용] 채용공고 전체 및 검수 목록 다중 조건 검색/페이징 조회
     *
     * @param cond 검색 필터 파라미터 DTO (검수 상태, 공고 구분 등)
     * @param pageable 페이징 파라미터
     * @return 채용공고 목록 요약 응답 DTO 페이징 객체
     */
    public Page<JobPostingSummaryResponseDTO> getStaffJobPostings(JobPostingSearchConditionDTO cond, Pageable pageable) {
        Page<JobPosting> postingPage = jobPostingRepository.searchStaffPostings(cond, pageable);
        return postingPage.map(this::convertToSummaryDTO);
    }

    /**
     * 채용공고 단건 상세 조회 (N+1 방지 Fetch Join 적용)
     *
     * @param jobPostingId 채용공고 PK
     * @return 채용공고 상세 응답 DTO
     */
    public JobPostingDetailResponseDTO getJobPostingDetail(Integer jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findByIdWithDetails(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        return convertToDetailDTO(jobPosting);
    }

    /**
     * [교직원/기업] 채용공고 신규 등록 (구인 신청 접수)
     *
     * @param requestDTO 공고 등록 요청 DTO
     * @return 생성된 채용공고 식별자 (PK)
     */
    @Transactional
    public Integer createJobPosting(JobPostingCreateRequestDTO requestDTO) {
        CompanyAccount companyAccount = companyAccountRepository.findById(requestDTO.getCompanyAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND));

        CommonCode ncsCode = findCommonCodeOrNull(requestDTO.getNcsCodeId());
        CommonCode regionCode = findCommonCodeOrNull(requestDTO.getRegionCodeId());

        if (requestDTO.getApplicationStartsAt() != null && requestDTO.getApplicationStartsAt().isAfter(requestDTO.getApplicationEndsAt())) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_PERIOD);
        }

        Instant startsAt = requestDTO.getApplicationStartsAt() != null ? requestDTO.getApplicationStartsAt().toInstant() : null;
        Instant endsAt = requestDTO.getApplicationEndsAt().toInstant();
        JsonNode qualJsonNode = mapToJsonNode(requestDTO.getQualificationData());

        JobPosting jobPosting = JobPosting.builder()
                .companyAccount(companyAccount)
                .ncsCode(ncsCode)
                .regionCode(regionCode)
                .postingTitle(requestDTO.getPostingTitle())
                .jobDescription(requestDTO.getJobDescription())
                .recruitmentCount(requestDTO.getRecruitmentCount())
                .employmentType(requestDTO.getEmploymentType())
                .salaryText(requestDTO.getSalaryText())
                .qualificationData(qualJsonNode)
                .applicationStartsAt(startsAt)
                .applicationEndsAt(endsAt)
                .postingType(requestDTO.getPostingType())
                .benefitType(requestDTO.getBenefitType())
                .build();

        JobPosting savedPosting = jobPostingRepository.save(jobPosting);
        log.info("[JobPostingService] 채용공고 신규 등록 완료. ID: {}, 제목: {}", savedPosting.getJobPostingId(), savedPosting.getPostingTitle());
        return savedPosting.getJobPostingId();
    }

    /**
     * [교직원/기업] 채용공고 내용 수정
     *
     * @param jobPostingId 공고 식별자 (PK)
     * @param requestDTO 공고 수정 요청 DTO
     */
    @Transactional
    public void updateJobPosting(Integer jobPostingId, JobPostingUpdateRequestDTO requestDTO) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        if (requestDTO.getApplicationStartsAt() != null && requestDTO.getApplicationStartsAt().isAfter(requestDTO.getApplicationEndsAt())) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_PERIOD);
        }

        CommonCode ncsCode = findCommonCodeOrNull(requestDTO.getNcsCodeId());
        CommonCode regionCode = findCommonCodeOrNull(requestDTO.getRegionCodeId());

        Instant startsAt = requestDTO.getApplicationStartsAt() != null ? requestDTO.getApplicationStartsAt().toInstant() : null;
        Instant endsAt = requestDTO.getApplicationEndsAt().toInstant();
        JsonNode qualJsonNode = mapToJsonNode(requestDTO.getQualificationData());

        jobPosting.update(
                ncsCode,
                regionCode,
                requestDTO.getPostingTitle(),
                requestDTO.getJobDescription(),
                requestDTO.getRecruitmentCount(),
                requestDTO.getEmploymentType(),
                requestDTO.getSalaryText(),
                qualJsonNode,
                startsAt,
                endsAt,
                requestDTO.getPostingType(),
                requestDTO.getBenefitType()
        );

        log.info("[JobPostingService] 채용공고 수정 완료. ID: {}", jobPostingId);
    }

    /**
     * [교직원 전용] 채용공고 검수 (승인 / 반려) 처리
     *
     * @param jobPostingId 공고 식별자 (PK)
     * @param reviewerUserId 로그인한 교직원 사용자 식별자 (reviewed_by)
     * @param requestDTO 검수 요청 DTO
     */
    @Transactional
    public void reviewJobPosting(Integer jobPostingId, Integer reviewerUserId, JobPostingReviewRequestDTO requestDTO) {
        // TODO: 이후 실제 데이터 넣어서 처리 필요 - 부서 권한 검증: 취창업지원팀 교직원 또는 관리자 여부 확인
        validateCareerStaff(reviewerUserId);

        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        String targetStatus = requestDTO.getReviewStatus();

        if ("REJECTED".equalsIgnoreCase(targetStatus)) {
            if (!StringUtils.hasText(requestDTO.getRejectionReason())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 시 반려 사유 입력은 필수입니다.");
            }
        } else if (!"APPROVED".equalsIgnoreCase(targetStatus)) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATUS);
        }

        jobPosting.review(targetStatus, requestDTO.getRejectionReason(), reviewerUserId);
        log.info("[JobPostingService] 채용공고 검수 처리 완료. ID: {}, 상태: {}, 검수자 ID: {}", jobPostingId, targetStatus, reviewerUserId);
    }

    /**
     * 교직원 전용 채용공고 검수에사, 부서 권한 검증 헬퍼 메서드 추가
     *
     * 예시: user의 부서 정보나 권한을 확인하여 취창업 관련 부서/관리자가 아니면 거부
     * if (!user.isCareerStaffOrAdmin()) { ... throw new BusinessException(ErrorCode.ACCESS_DENIED); }
     */
    private void validateCareerStaff(Integer userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * [교직원 전용] 채용공고 삭제
     *
     * @param jobPostingId 공고 식별자 (PK)
     */
    @Transactional
    public void deleteJobPosting(Integer jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        jobPostingRepository.delete(jobPosting);
        log.info("[JobPostingService] 채용공고 삭제 완료. ID: {}", jobPostingId);
    }

    // --- Private 매핑 및 헬퍼 메서드 ---
    private CommonCode findCommonCodeOrNull(Integer codeId) {
        if (codeId == null) {
            return null;
        }
        return commonCodeRepository.findById(codeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "일치하는 공통코드 정보를 찾을 수 없습니다."));
    }

    private JsonNode mapToJsonNode(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return objectMapper.valueToTree(map);
    }

    private Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        return objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
    }

    private JobPostingSummaryResponseDTO convertToSummaryDTO(JobPosting jp) {
        return JobPostingSummaryResponseDTO.builder()
                .jobPostingId(jp.getJobPostingId())
                .companyAccountId(jp.getCompanyAccount().getCompanyAccountId())
                .companyName(jp.getCompanyAccount().getCompanyName())
                .ncsCodeName(jp.getNcsCode() != null ? jp.getNcsCode().getCodeName() : null)
                .regionCodeName(jp.getRegionCode() != null ? jp.getRegionCode().getCodeName() : null)
                .postingTitle(jp.getPostingTitle())
                .employmentType(jp.getEmploymentType())
                .salaryText(jp.getSalaryText())
                .applicationStartsAt(toOffsetDateTime(jp.getApplicationStartsAt()))
                .applicationEndsAt(toOffsetDateTime(jp.getApplicationEndsAt()))
                .postingType(jp.getPostingType())
                .reviewStatus(jp.getReviewStatus())
                .postingStatus(jp.getPostingStatus())
                .isScrapped(false)
                .build();
    }

    private JobPostingDetailResponseDTO convertToDetailDTO(JobPosting jp) {
        return JobPostingDetailResponseDTO.builder()
                .jobPostingId(jp.getJobPostingId())
                .companyAccountId(jp.getCompanyAccount().getCompanyAccountId())
                .companyName(jp.getCompanyAccount().getCompanyName())
                .ncsCodeId(jp.getNcsCode() != null ? jp.getNcsCode().getCodeId() : null)
                .ncsCodeName(jp.getNcsCode() != null ? jp.getNcsCode().getCodeName() : null)
                .regionCodeId(jp.getRegionCode() != null ? jp.getRegionCode().getCodeId() : null)
                .regionCodeName(jp.getRegionCode() != null ? jp.getRegionCode().getCodeName() : null)
                .reviewedBy(jp.getReviewedBy())
                .postingTitle(jp.getPostingTitle())
                .jobDescription(jp.getJobDescription())
                .recruitmentCount(jp.getRecruitmentCount())
                .employmentType(jp.getEmploymentType())
                .salaryText(jp.getSalaryText())
                .qualificationData(jsonNodeToMap(jp.getQualificationData()))
                .applicationStartsAt(toOffsetDateTime(jp.getApplicationStartsAt()))
                .applicationEndsAt(toOffsetDateTime(jp.getApplicationEndsAt()))
                .postingType(jp.getPostingType())
                .benefitType(jp.getBenefitType())
                .reviewStatus(jp.getReviewStatus())
                .reviewedAt(toOffsetDateTime(jp.getReviewedAt()))
                .rejectionReason(jp.getRejectionReason())
                .postingStatus(jp.getPostingStatus())
                .submittedAt(toOffsetDateTime(jp.getSubmittedAt()))
                .publishedAt(toOffsetDateTime(jp.getPublishedAt()))
                .createdAt(toOffsetDateTime(jp.getCreatedAt()))
                .updatedAt(toOffsetDateTime(jp.getUpdatedAt()))
                .isScrapped(false)
                .build();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }
}