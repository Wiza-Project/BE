package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageBenefitPolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageBenefitPolicyUpdateRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageBenefitPolicyResponseDTO;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
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

    // 학기 코드를 생략한 등록 요청에 채워 넣는 기본값. "연간 공통" 정책을 의미한다(MileageBenefitPolicy 엔티티 기본값과 동일).
    private static final String DEFAULT_SEMESTER_CODE = "ALL";

    private final MileageBenefitPolicyRepository benefitPolicyRepository;

    @Transactional
    public MileageBenefitPolicyResponseDTO register(
            MileageBenefitPolicyRegisterRequestDTO request,
            Integer staffId
    ) {
        String semesterCode = resolveSemesterCode(request.semesterCode());
        validatePeriod(request.applicationStartsAt(), request.applicationEndsAt());

        MileageBenefitPolicy policy = MileageBenefitPolicy.create(
                request.benefitType(),
                request.academicYear(),
                semesterCode,
                request.benefitName(),
                request.minimumPoints(),
                request.benefitAmount(),
                request.criteriaData(),
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
            Integer academicYear,
            String semesterCode,
            Boolean active,
            Pageable pageable
    ) {
        Specification<MileageBenefitPolicy> spec = buildFilter(benefitType, academicYear, semesterCode, active);
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
        MileageBenefitPolicy policy = benefitPolicyRepository.findById(benefitPolicyId)
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
                request.criteriaData(),
                request.applicationStartsAt(),
                request.applicationEndsAt(),
                request.active()
        );

        return MileageBenefitPolicyResponseDTO.from(policy);
    }

    private String resolveSemesterCode(String semesterCode) {
        return (semesterCode == null || semesterCode.isBlank()) ? DEFAULT_SEMESTER_CODE : semesterCode;
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
            Integer academicYear,
            String semesterCode,
            Boolean active
    ) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (benefitType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("benefitType"), benefitType));
            }
            if (academicYear != null) {
                predicate = cb.and(predicate, cb.equal(root.get("academicYear"), academicYear));
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
