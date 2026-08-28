package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.mileage.DTO.request.ExtracurricularMileagePolicySetupRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileagePolicyResponseDTO;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageActivityTypeRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** C100~C600 핵심역량별 비교과 활동유형과 5점 정책을 일괄 구성한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class ExtracurricularMileagePolicySetupService {

    private static final String DEFAULT_SEMESTER_CODE = "ALL";

    private final CompetencyRepository competencyRepository;
    private final MileageActivityTypeRepository activityTypeRepository;
    private final MileagePolicyRepository policyRepository;
    private final MileagePolicyService mileagePolicyService;

    public List<MileagePolicyResponseDTO> setup(
            ExtracurricularMileagePolicySetupRequestDTO request,
            Integer staffId) {
        validatePeriod(request.validFrom(), request.validTo());
        String semesterCode = resolveSemesterCode(request.semesterCode());
        Map<String, Competency> competencies = competencyRepository
                .findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .collect(Collectors.toMap(Competency::getCompetencyCode, Function.identity()));

        return ExtracurricularMileagePolicyDefinition.CORE_COMPETENCY_CODES.stream()
                .map(code -> setupForCompetency(
                        competencies.get(code), request, semesterCode, staffId, code))
                .toList();
    }

    private MileagePolicyResponseDTO setupForCompetency(
            Competency competency,
            ExtracurricularMileagePolicySetupRequestDTO request,
            String semesterCode,
            Integer staffId,
            String competencyCode) {
        if (competency == null) {
            throw new BusinessException(
                    ErrorCode.COMPETENCY_NOT_FOUND,
                    "비교과 정책에 필요한 핵심역량을 찾을 수 없습니다: " + competencyCode);
        }

        MileageActivityType activityType = ensureActivityType(competency, staffId, competencyCode);
        MileagePolicy latestPolicy = policyRepository
                .findTopByActivityType_ActivityTypeIdAndAcademicYearAndSemesterCodeOrderByVersionNoDesc(
                        activityType.getActivityTypeId(), request.academicYear(), semesterCode)
                .orElse(null);

        if (isSamePolicy(latestPolicy, request)) {
            return MileagePolicyResponseDTO.from(latestPolicy);
        }

        return mileagePolicyService.register(
                new MileagePolicyRegisterRequestDTO(
                        activityType.getActivityTypeId(),
                        request.academicYear(),
                        semesterCode,
                        ExtracurricularMileagePolicyDefinition.POINTS,
                        null,
                        request.validFrom(),
                        request.validTo(),
                        null),
                staffId);
    }

    private MileageActivityType ensureActivityType(
            Competency competency,
            Integer staffId,
            String competencyCode) {
        String activityCode = ExtracurricularMileagePolicyDefinition.activityCodeFor(competencyCode);
        MileageActivityType existing = activityTypeRepository.findByActivityCode(activityCode).orElse(null);
        if (existing != null) {
            boolean sameCompetency = existing.getCompetency() != null
                    && Objects.equals(competency.getCompetencyId(), existing.getCompetency().getCompetencyId());
            if (!existing.isActive()
                    || !sameCompetency
                    || !ExtracurricularMileagePolicyDefinition.isExtracurricular(existing)) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "비교과 활동유형 코드가 다른 핵심역량 또는 설정과 연결되어 있습니다: " + activityCode);
            }
            return existing;
        }

        return activityTypeRepository.saveAndFlush(MileageActivityType.create(
                competency,
                activityCode,
                ExtracurricularMileagePolicyDefinition.CATEGORY_CODE,
                "비교과-" + competency.getCompetencyName(),
                ExtracurricularMileagePolicyDefinition.EARNING_ROUTE,
                staffId));
    }

    private boolean isSamePolicy(MileagePolicy policy,
                                 ExtracurricularMileagePolicySetupRequestDTO request) {
        return policy != null
                && "ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())
                && ExtracurricularMileagePolicyDefinition.isExtracurricular(policy.getActivityType())
                && policy.getPoints() != null
                && policy.getPoints().compareTo(ExtracurricularMileagePolicyDefinition.POINTS) == 0
                && request.validFrom().equals(policy.getValidFrom())
                && java.util.Objects.equals(request.validTo(), policy.getValidTo());
    }

    private String resolveSemesterCode(String semesterCode) {
        return semesterCode == null || semesterCode.isBlank() ? DEFAULT_SEMESTER_CODE : semesterCode;
    }

    private void validatePeriod(LocalDate validFrom, LocalDate validTo) {
        if (validTo != null && !validFrom.isBefore(validTo)) {
            throw new BusinessException(ErrorCode.MILEAGE_POLICY_INVALID_PERIOD);
        }
    }
}
