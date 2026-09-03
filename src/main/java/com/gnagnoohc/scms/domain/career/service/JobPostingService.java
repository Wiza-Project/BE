package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.career.dto.posting.*;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.domain.career.helper.CareerBindingHelper;
import com.gnagnoohc.scms.domain.career.helper.CareerSecurityHelper;
import com.gnagnoohc.scms.domain.career.repository.CompanyAccountRepository;
import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.helper.FileUploadValidator;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;

/**
 * 채용공고 핵심 비즈니스 로직 서비스
 *
 * <p><strong>[설계 원칙 및 사용자 역할별 라이프사이클 관리 기준]</strong></p>
 * <p>학생의 채용공고 탐색 및 교직원의 구인 신청 검수/게시 라이프사이클을 총괄 &
 * 비즈니스 정합성 검증, DTO-Entity 간 데이터 바인딩, JSON/시간대 변환 전담</p>
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
 *   <li><b>DateTimeUtils 공통 유틸을 활용한 시간대 표준화:</b> DB 영속화 기준 UTC {@code Instant}와 API 응답 기준 KST (Asia/Seoul) {@code OffsetDateTime} 간 표준 변환 지원</li>
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
    private final CareerBindingHelper careerBindingHelper;
    private final CareerSecurityHelper careerSecurityHelper;

    private final FileGroupService fileGroupService;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;

    // 빈 주입 의존성 경고 방지 및 독립적 JSON 직렬화/역직렬화를 위한 객체 생성
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [학생용] 채용공고 다중 조건 검색 및 목록 페이징 조회
     *
     * @param cond     검색 필터 파라미터 DTO (직무, 지역, 기업명, 고용형태, 공고구분 등)
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
     * @param cond     검색 필터 파라미터 DTO (검수 상태, 공고 구분 등)
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
     * [교직원/기업] 채용공고 신규 등록 (구인 신청 접수, 선택적 FileGroup 바인딩 포함)
     *
     * @param requestDTO 공고 등록 요청 DTO
     * @return 생성된 채용공고 식별자 (PK)
     */
    @Transactional
    public Integer createJobPosting(JobPostingCreateRequestDTO requestDTO) {
        CompanyAccount companyAccount = companyAccountRepository.findById(requestDTO.getCompanyAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND));

        // 미인증 기업 차단 검증
        if (!"VERIFIED".equalsIgnoreCase(companyAccount.getVerificationStatus())) {
            throw new BusinessException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND, "인증 심사가 완료(승인)된 협약 기업만 채용공고를 등록할 수 있습니다.");
        }

        CommonCode ncsCode = careerBindingHelper.findValidCommonCodeOrNull(requestDTO.getNcsCodeId());
        CommonCode regionCode = careerBindingHelper.findValidRegionCodeOrNull(requestDTO.getRegionCodeId());
        FileGroup fileGroup = careerBindingHelper.findValidFileGroupOrNull(requestDTO.getFileGroupId());

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
                .fileGroup(fileGroup)
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
     * [교직원/관리자 전용] 채용공고 포스터(이미지 및 PDF) 단독 업로드 처리
     * 공통모듈에서 설계한 파일 첨부 모듈 적용
     */
    @Transactional
    public void uploadJobPoster(Integer jobPostingId, MultipartFile file, Integer uploaderUserId) {
        careerSecurityHelper.validateAndGetCareerStaff(uploaderUserId);

        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        if (file == null || file.isEmpty()) {
            return;
        }

        // 지원 포맷 검증 (jpg, jpeg, png, gif, webp, pdf)
        fileUploadValidator.validate(file, FileUploadValidator.SUPPORTED_EXTENSIONS);

        FileGroup group = jobPosting.getFileGroup();
        if (group == null) {
            group = fileGroupService.createGroup();
            jobPosting.setFileGroup(group);
        }

        fileStorageService.store(file, group, uploaderUserId);
        log.info("[JobPostingService] 채용공고 포스터 파일 업로드 완료. jobPostingId: {}, fileGroupId: {}",
                jobPostingId, group.getFileGroupId());
    }

    /**
     * [교직원/기업] 채용공고 내용 수정
     *
     * @param jobPostingId 공고 식별자 (PK)
     * @param requestDTO   공고 수정 요청 DTO
     */
    @Transactional
    public void updateJobPosting(Integer jobPostingId, JobPostingUpdateRequestDTO requestDTO) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        if (requestDTO.getApplicationStartsAt() != null && requestDTO.getApplicationStartsAt().isAfter(requestDTO.getApplicationEndsAt())) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_PERIOD);
        }

        CommonCode ncsCode = careerBindingHelper.findValidCommonCodeOrNull(requestDTO.getNcsCodeId());
        CommonCode regionCode = careerBindingHelper.findValidRegionCodeOrNull(requestDTO.getRegionCodeId());
        FileGroup fileGroup = careerBindingHelper.findValidFileGroupOrNull(requestDTO.getFileGroupId());

        Instant startsAt = requestDTO.getApplicationStartsAt() != null ? requestDTO.getApplicationStartsAt().toInstant() : null;
        Instant endsAt = requestDTO.getApplicationEndsAt().toInstant();
        JsonNode qualJsonNode = mapToJsonNode(requestDTO.getQualificationData());

        jobPosting.update(
                ncsCode,
                regionCode,
                fileGroup,
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
     * @param jobPostingId   공고 식별자 (PK)
     * @param reviewerUserId 로그인한 교직원 사용자 식별자 (reviewed_by)
     * @param requestDTO     검수 요청 DTO
     */
    @Transactional
    public void reviewJobPosting(Integer jobPostingId, Integer reviewerUserId, JobPostingReviewRequestDTO requestDTO) {
        careerSecurityHelper.validateAndGetCareerStaff(reviewerUserId);

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
     * [교직원 전용] 채용공고 게시 상태 직접 변경 (게시/마감)
     *
     * @param jobPostingId  공고 식별자 (PK)
     * @param postingStatus 변경할 게시 상태 ('PUBLISHED' 또는 'CLOSED')
     */
    @Transactional
    public void updatePostingStatus(Integer jobPostingId, String postingStatus) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        if ("PUBLISHED".equalsIgnoreCase(postingStatus)) {
            jobPosting.review("APPROVED", null, null);
        } else if ("CLOSED".equalsIgnoreCase(postingStatus)) {
            jobPosting.review("CLOSED", null, null);
        } else if ("DRAFT".equalsIgnoreCase(postingStatus)) {
            jobPosting.review("REQUESTED", null, null);
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 게시 상태입니다.");
        }

        log.info("[JobPostingService] 채용공고 게시 상태 변경 완료. ID: {}, 상태: {}", jobPostingId, postingStatus);
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
        return objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 채용공고 엔티티를 클라이언트 목록/탐색용 요약 응답 DTO로 매핑 변환
     * 일시 데이터는 공통 시간 유틸리티({@link DateTimeUtils})지정한 KST 오프셋 메소드를 호출-변환 처리
     *
     * @param jp 채용공고 엔티티
     * @return 목록 표시용 채용공고 요약 Response DTO
     */
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
                .applicationStartsAt(DateTimeUtils.toKstOffsetDateTime(jp.getApplicationStartsAt()))
                .applicationEndsAt(DateTimeUtils.toKstOffsetDateTime(jp.getApplicationEndsAt()))
                .postingType(jp.getPostingType())
                .reviewStatus(jp.getReviewStatus())
                .postingStatus(jp.getPostingStatus())
                .isScrapped(false)
                .build();
    }

    /**
     * 채용공고 엔티티를 클라이언트 단건 상세 응답 DTO로 매핑 변환
     * 일시 데이터는 공통 시간 유틸리티({@link DateTimeUtils})지정한 KST 오프셋 메소드를 호출-변환 처리
     *
     * @param jp 채용공고 엔티티 원장
     * @return 채용공고 상세 Response DTO
     */
    private JobPostingDetailResponseDTO convertToDetailDTO(JobPosting jp) {
        return JobPostingDetailResponseDTO.builder()
                .jobPostingId(jp.getJobPostingId())
                .companyAccountId(jp.getCompanyAccount().getCompanyAccountId())
                .companyName(jp.getCompanyAccount().getCompanyName())
                .ncsCodeId(jp.getNcsCode() != null ? jp.getNcsCode().getCodeId() : null)
                .ncsCodeName(jp.getNcsCode() != null ? jp.getNcsCode().getCodeName() : null)
                .regionCodeId(jp.getRegionCode() != null ? jp.getRegionCode().getCodeId() : null)
                .regionCodeName(jp.getRegionCode() != null ? jp.getRegionCode().getCodeName() : null)
                .fileGroupId(jp.getFileGroup() != null ? jp.getFileGroup().getFileGroupId() : null)
                .reviewedBy(jp.getReviewedBy())
                .postingTitle(jp.getPostingTitle())
                .jobDescription(jp.getJobDescription())
                .recruitmentCount(jp.getRecruitmentCount())
                .employmentType(jp.getEmploymentType())
                .salaryText(jp.getSalaryText())
                .qualificationData(jsonNodeToMap(jp.getQualificationData()))
                .applicationStartsAt(DateTimeUtils.toKstOffsetDateTime(jp.getApplicationStartsAt()))
                .applicationEndsAt(DateTimeUtils.toKstOffsetDateTime(jp.getApplicationEndsAt()))
                .postingType(jp.getPostingType())
                .benefitType(jp.getBenefitType())
                .reviewStatus(jp.getReviewStatus())
                .reviewedAt(DateTimeUtils.toKstOffsetDateTime(jp.getReviewedAt()))
                .rejectionReason(jp.getRejectionReason())
                .postingStatus(jp.getPostingStatus())
                .submittedAt(DateTimeUtils.toKstOffsetDateTime(jp.getSubmittedAt()))
                .publishedAt(DateTimeUtils.toKstOffsetDateTime(jp.getPublishedAt()))
                .createdAt(DateTimeUtils.toKstOffsetDateTime(jp.getCreatedAt()))
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(jp.getUpdatedAt()))
                .isScrapped(false)
                .build();
    }
}