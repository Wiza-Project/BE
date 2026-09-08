package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageBenefitPolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageBenefitPolicyUpdateRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageBenefitPolicyResponseDTO;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.support.MileageJsonNodeConverter;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageBenefitPolicyService {

    private final MileageBenefitPolicyRepository benefitPolicyRepository;
    private final MileageSemesterCodeValidator semesterCodeValidator;

    @Transactional
    public MileageBenefitPolicyResponseDTO register(
            MileageBenefitPolicyRegisterRequestDTO request,
            Integer staffId
    ) {
        String semesterCode = semesterCodeValidator.resolveSemesterCodeOrDefault(request.semesterCode());
        validatePeriod(request.applicationStartsAt(), request.applicationEndsAt());

        MileageBenefitPolicy policy = MileageBenefitPolicy.create(
                request.benefitType(),
                semesterCode,
                request.benefitName(),
                request.minimumPoints(),
                request.benefitAmount(),
                MileageJsonNodeConverter.toJackson2(request.criteriaData()),
                request.applicationStartsAt(),
                request.applicationEndsAt(),
                staffId,
                request.benefitGroupCode(),
                request.cumulativeYears(),
                request.requiresExactPoints()
        );

        return MileageBenefitPolicyResponseDTO.from(benefitPolicyRepository.saveAndFlush(policy));
    }

    public PageResponse<MileageBenefitPolicyResponseDTO> list(
            String benefitType,
            String semesterCode,
            Boolean active,
            Pageable pageable
    ) {
        String normalizedSemesterCode = semesterCodeValidator.normalizeSemesterCodeIfPresent(semesterCode);
        Specification<MileageBenefitPolicy> spec = buildFilter(benefitType, normalizedSemesterCode, active);
        Page<MileageBenefitPolicy> page = benefitPolicyRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(MileageBenefitPolicyResponseDTO::from));
    }

    public MileageBenefitPolicyResponseDTO getDetail(Integer benefitPolicyId) {
        return benefitPolicyRepository.findById(benefitPolicyId)
                .map(MileageBenefitPolicyResponseDTO::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MILEAGE_BENEFIT_POLICY_NOT_FOUND));
    }

    @Transactional
    public MileageBenefitPolicyResponseDTO update(
            Integer benefitPolicyId,
            MileageBenefitPolicyUpdateRequestDTO request
    ) {
        MileageBenefitPolicy policy = benefitPolicyRepository.findByIdForUpdate(benefitPolicyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MILEAGE_BENEFIT_POLICY_NOT_FOUND));

        Instant applicationStartsAt = request.applicationStartsAt() != null
                ? request.applicationStartsAt() : policy.getApplicationStartsAt();
        Instant applicationEndsAt = request.applicationEndsAt() != null
                ? request.applicationEndsAt() : policy.getApplicationEndsAt();
        validatePeriod(applicationStartsAt, applicationEndsAt);

        policy.update(
                request.benefitName(),
                request.minimumPoints(),
                request.benefitAmount(),
                MileageJsonNodeConverter.toJackson2(request.criteriaData()),
                request.applicationStartsAt(),
                request.applicationEndsAt(),
                request.active(),
                request.benefitGroupCode(),
                request.cumulativeYears(),
                request.requiresExactPoints()
        );

        return MileageBenefitPolicyResponseDTO.from(policy);
    }

    // 신청 종료일(applicationEndsAt)은 nullable(마감 없음)이라 있을 때만 검사한다.
    private void validatePeriod(Instant applicationStartsAt, Instant applicationEndsAt) {
        if (applicationStartsAt != null && applicationEndsAt != null
                && !applicationStartsAt.isBefore(applicationEndsAt)) {
            throw new BusinessException(ErrorCode.MILEAGE_BENEFIT_POLICY_INVALID_PERIOD);
        }
    }

    private Specification<MileageBenefitPolicy> buildFilter(
            String benefitType,
            String semesterCode,
            Boolean active
    ) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (benefitType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("benefitType"), benefitType));
            }
            if (semesterCode != null) {
                predicate = cb.and(predicate, cb.equal(root.get("semesterCode"), semesterCode));
            }
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            return predicate;
        };
    }
}
