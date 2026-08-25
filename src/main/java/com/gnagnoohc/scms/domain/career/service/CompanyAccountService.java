package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.company.*;
import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
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
 * 협약 기업 메타데이터 관리 및 교직원 검증 비즈니스 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyAccountService {

    private static final String CAREER_EMPLOYMENT_DEPT = "D400";

    private final CompanyAccountRepository companyAccountRepository;
    private final AppUserRepository appUserRepository;

    /**
     * [기업/외부] 협약 기업 메타데이터 등록 및 제휴 신청
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
     * [교직원 전용] 기업 승인 / 반려 심사 처리
     */
    @Transactional
    public void verifyCompany(Integer companyAccountId, Integer reviewerUserId, CompanyVerifyRequestDTO requestDTO) {
        validateCareerStaff(reviewerUserId);

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
     * [교직원/관리자] 기업 목록 다중 검색 및 페이징 조회
     */
    public Page<CompanySummaryResponseDTO> searchCompanies(CompanySearchConditionDTO cond, Pageable pageable) {
        Page<CompanyAccount> page = companyAccountRepository.searchCompanies(cond, pageable);
        return page.map(this::convertToSummaryDTO);
    }

    /**
     * 기업 단건 상세 정보 조회
     */
    public CompanyDetailResponseDTO getCompanyDetail(Integer companyAccountId) {
        CompanyAccount company = companyAccountRepository.findById(companyAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND));

        return convertToDetailDTO(company);
    }

    /**
     * 교직원 권한(D400 취창업지원과) 검증
     */
    private void validateCareerStaff(Integer userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean isCareerStaff = user.getDepartmentCode() != null
                && CAREER_EMPLOYMENT_DEPT.equals(user.getDepartmentCode().getCode());

        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getUserType());

        if (!isCareerStaff && !isAdmin) {
            throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
        }
    }

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