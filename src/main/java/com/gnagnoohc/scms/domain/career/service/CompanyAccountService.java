package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.company.*;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import com.gnagnoohc.scms.domain.career.helper.CareerSecurityHelper;
import com.gnagnoohc.scms.domain.career.repository.CompanyAccountRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
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

/**
 * 협약 기업 메타데이터 관리 및 교직원 심사·검증 비즈니스 서비스
 *
 * <p><strong>[핵심 기능 및 도메인 규칙]</strong></p>
 * <ul>
 *   <li><b>기업 등록 신청:</b> 사업자등록번호 중복 검증 후 대기 상태({@code PENDING})로 영속화</li>
 *   <li><b>심사 및 승인/반려:</b> 취창업지원과({@code D400}) 교직원 및 총괄 관리자({@code ADMIN}) 인가 검증 후 상태 전이</li>
 *   <li><b>기업 탐색 및 상세 조회:</b> 동적 조건 기반 페이징 검색 및 KST 오프셋 일시 포맷팅 제공</li>
 * </ul>
 *
 * @author YUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyAccountService {

    private final CompanyAccountRepository companyAccountRepository;
    private final CareerSecurityHelper careerSecurityHelper;

    /**
     * [기업/외부] 협약 기업 메타데이터 등록 및 제휴 신청
     *
     * <p>사업자등록번호 중복 여부를 사전 검증하고, 검증 상태 {@code PENDING} 및 계정 상태 {@code ACTIVE}로 등록<br>
     * 기업 로그인 UI가 배제된 현재 스코프에 맞춰 로그인 자격증명 필드는 null 허용 처리</p>
     *
     * @param requestDTO 기업 정보, 사업자등록번호, 담당자 연락처 등이 포함된 등록 요청 DTO
     * @return 생성된 협약 기업 계정 식별자 (PK)
     * @throws BusinessException 이미 등록된 사업자등록번호인 경우 ({@link ErrorCode#DUPLICATE_COMPANY_ACCOUNT_NO})
     */
    @Transactional
    public Integer registerCompany(CompanyRegisterRequestDTO requestDTO) {
        if (companyAccountRepository.existsByBusinessRegistrationNo(requestDTO.getBusinessRegistrationNo())) {
            throw new BusinessException(ErrorCode.DUPLICATE_COMPANY_ACCOUNT_NO);
        }

        CompanyAccount company = CompanyAccount.builder()
                .businessRegistrationNo(requestDTO.getBusinessRegistrationNo())
                .companyName(requestDTO.getCompanyName())
                .representativeName(requestDTO.getRepresentativeName())
                .address(requestDTO.getAddress())
                .contactName(requestDTO.getContactName())
                .contactEmail(requestDTO.getContactEmail())
                .contactPhone(requestDTO.getContactPhone())
                .verificationStatus("PENDING")
                .accountStatus("ACTIVE")
                .build();

        CompanyAccount saved = companyAccountRepository.save(company);
        log.info("[CompanyAccountService] 기업 등록 신청 완료. ID: {}, 기업명: {}", saved.getCompanyAccountId(), saved.getCompanyName());
        return saved.getCompanyAccountId();
    }

    /**
     * [교직원 전용] 협약 기업 등록 승인 또는 반려 심사 처리
     *
     * <p>심사 요청자가 취창업지원과({@code D400}) 소속 교직원 또는 시스템 관리자({@code ADMIN})인지 {@link CareerSecurityHelper}를 통해 검증<br>
     * 승인 시 {@code VERIFIED} 상태로 전환 및 심사 일시를 기록하고, 반려 시 {@code REJECTED} 및 {@code INACTIVE} 상태로 전환</p>
     *
     * @param companyAccountId 대상 기업 계정 식별자 (PK)
     * @param reviewerUserId   심사를 진행하는 교직원 사용자 식별자 (PK)
     * @param requestDTO       목표 심사 상태값({@code VERIFIED} / {@code REJECTED}) 및 반려 사유가 포함된 DTO
     * @throws BusinessException 권한이 유효하지 않은 경우 ({@link ErrorCode#DEPARTMENT_FORBIDDEN}),
     *                           기업이 존재하지 않는 경우 ({@link ErrorCode#COMPANY_ACCOUNT_NOT_FOUND}),
     *                           심사 상태값이 올바르지 않은 경우 ({@link ErrorCode#INVALID_INPUT})
     */
    @Transactional
    public void verifyCompany(Integer companyAccountId, Integer reviewerUserId, CompanyVerifyRequestDTO requestDTO) {
        careerSecurityHelper.validateAndGetCareerStaff(reviewerUserId);

        CompanyAccount company = companyAccountRepository.findById(companyAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND));

        String targetStatus = requestDTO.getVerificationStatus();

        if ("VERIFIED".equalsIgnoreCase(targetStatus)) {
            company.verify(reviewerUserId, Instant.now());
            log.info("[CompanyAccountService] 기업 승인 완료. 기업 ID: {}, 승인 교직원 ID: {}", companyAccountId, reviewerUserId);
        } else if ("REJECTED".equalsIgnoreCase(targetStatus)) {
            company.reject(reviewerUserId);
            log.info("[CompanyAccountService] 기업 반려 완료. 기업 ID: {}, 사유: {}", companyAccountId, requestDTO.getRejectReason());
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 검증 상태값입니다.");
        }
    }

    /**
     * [교직원/관리자] 기업 목록 다중 조건 검색 및 페이징 조회
     *
     * @param cond     검증 상태, 기업명 등 검색 필터 조건 DTO
     * @param pageable 페이징 및 정렬 파라미터
     * @return 검색 조건에 부합하는 협약 기업 요약 정보 페이징 응답 객체
     */
    public Page<CompanySummaryResponseDTO> searchCompanies(CompanySearchConditionDTO cond, Pageable pageable) {
        Page<CompanyAccount> page = companyAccountRepository.searchCompanies(cond, pageable);
        return page.map(this::convertToSummaryDTO);
    }

    /**
     * 협약 기업 단건 상세 정보 조회
     *
     * @param companyAccountId 조회 대상 협약 기업 식별자 (PK)
     * @return 기업 상세 메타데이터 및 승인·심사 상태 응답 DTO
     * @throws BusinessException 해당 식별자의 기업 정보가 존재하지 않는 경우 ({@link ErrorCode#COMPANY_ACCOUNT_NOT_FOUND})
     */
    public CompanyDetailResponseDTO getCompanyDetail(Integer companyAccountId) {
        CompanyAccount company = companyAccountRepository.findById(companyAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND));

        return convertToDetailDTO(company);
    }

    /**
     * {@link CompanyAccount} 엔티티를 목록 조회용 요약 Response DTO로 매핑 변환
     *
     * @param ca 협약 기업 엔티티 원장
     * @return 요약 응답 DTO
     */
    private CompanySummaryResponseDTO convertToSummaryDTO(CompanyAccount ca) {
        return CompanySummaryResponseDTO.builder()
                .companyAccountId(ca.getCompanyAccountId())
                .businessRegistrationNo(ca.getBusinessRegistrationNo())
                .companyName(ca.getCompanyName())
                .representativeName(ca.getRepresentativeName())
                .contactName(ca.getContactName())
                .contactEmail(ca.getContactEmail())
                .verificationStatus(ca.getVerificationStatus())
                .accountStatus(ca.getAccountStatus())
                .createdAt(DateTimeUtils.toKstOffsetDateTime(ca.getCreatedAt()))
                .build();
    }

    /**
     * {@link CompanyAccount} 엔티티를 단건 상세 조회용 Response DTO로 매핑 변환
     *
     * @param ca 협약 기업 엔티티 원장
     * @return 상세 응답 DTO
     */
    private CompanyDetailResponseDTO convertToDetailDTO(CompanyAccount ca) {
        return CompanyDetailResponseDTO.builder()
                .companyAccountId(ca.getCompanyAccountId())
                .businessRegistrationNo(ca.getBusinessRegistrationNo())
                .companyName(ca.getCompanyName())
                .representativeName(ca.getRepresentativeName())
                .address(ca.getAddress())
                .contactName(ca.getContactName())
                .contactEmail(ca.getContactEmail())
                .contactPhone(ca.getContactPhone())
                .verificationStatus(ca.getVerificationStatus())
                .verifiedBy(ca.getVerifiedBy())
                .verifiedAt(DateTimeUtils.toKstOffsetDateTime(ca.getVerifiedAt()))
                .accountStatus(ca.getAccountStatus())
                .createdAt(DateTimeUtils.toKstOffsetDateTime(ca.getCreatedAt()))
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(ca.getUpdatedAt()))
                .build();
    }
}