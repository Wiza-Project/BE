package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageExternalActivityClaimRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageEvidenceFileUploadResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageExternalActivityClaimResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageExternalActivityPolicyResponse;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.ExternalActivityClaimRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageActivityTypeRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.helper.FileUploadValidator;
import com.gnagnoohc.scms.global.common.repository.FileGroupRepository;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.DbConstraintViolationMatcher;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 학생의 외부활동 증빙 파일 업로드와 마일리지 신청 제출을 담당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageExternalActivityClaimService {

    private static final Set<String> EVIDENCE_FILE_EXTENSIONS = Set.of("pdf");
    /** 외부활동 신청 화면·검증에서 제외할 route. 프로그램 이수/역량진단 완료는 각자의 전용 흐름으로만 적립된다. */
    private static final Set<String> EXCLUDED_EARNING_ROUTES = Set.of(
            ExtracurricularMileagePolicyDefinition.EARNING_ROUTE,
            CompetencyDiagnosisMileagePolicyDefinition.EARNING_ROUTE);
    private static final String EXTERNAL_CLAIM_FILE_GROUP_CONSTRAINT =
            "uq_external_activity_claim_file_group_id";

    private final ExternalActivityClaimRepository externalActivityClaimRepository;
    private final MileageActivityTypeRepository activityTypeRepository;
    private final MileagePolicyRepository policyRepository;
    private final AppUserRepository appUserRepository;
    private final FileGroupRepository fileGroupRepository;
    private final FileGroupService fileGroupService;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;

    /** 학생 외부활동 등록 화면에 표시할 활성 정책을 활동 유형별 최신 버전으로 반환한다. */
    public List<MileageExternalActivityPolicyResponse> listAvailablePolicies(LocalDate activityDate) {
        LocalDate asOfDate = activityDate == null ? LocalDate.now() : activityDate;

        Map<Integer, MileagePolicy> latestPoliciesByActivityType = new LinkedHashMap<>();
        for (MileagePolicy policy : policyRepository.findActiveExternalPoliciesOn(
                asOfDate, uppercaseExcludedEarningRoutes())) {
            latestPoliciesByActivityType.putIfAbsent(
                    policy.getActivityType().getActivityTypeId(), policy);
        }

        return latestPoliciesByActivityType.values().stream()
                .map(MileageExternalActivityPolicyResponse::from)
                .toList();
    }

    /** 비교과 프로그램 운영계획서 업로드와 동일하게 PDF 1개를 별도 파일 그룹에 저장한다. */
    @Transactional
    public MileageEvidenceFileUploadResponse uploadEvidence(MultipartFile file, Integer uploaderId) {
        requireUserId(uploaderId);
        fileUploadValidator.validate(file, EVIDENCE_FILE_EXTENSIONS);

        FileGroup fileGroup = fileGroupService.createGroup();
        StoredFile storedFile = fileStorageService.store(file, fileGroup, uploaderId);
        return new MileageEvidenceFileUploadResponse(
                fileGroup.getFileGroupId(), storedFile.getOriginalFileName());
    }

    /** 증빙 파일 그룹과 적용 정책을 검증한 뒤 심사 대기 상태의 신청을 저장한다. */
    @Transactional
    public MileageExternalActivityClaimResponse submit(
            MileageExternalActivityClaimRequest request,
            Integer studentId
    ) {
        validateRequest(request);
        Integer validStudentId = requireUserId(studentId);

        AppUser student = appUserRepository.findById(validStudentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        MileageActivityType activityType = activityTypeRepository.findById(request.activityTypeId())
                .filter(MileageActivityType::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.MILEAGE_ACTIVITY_TYPE_NOT_FOUND));

        validateExternalActivityType(activityType);
        MileagePolicy policy = findApplicablePolicy(activityType, request.activityDate());
        FileGroup fileGroup = validateFileGroup(request.fileGroupId(), validStudentId);

        ExternalActivityClaim claim = ExternalActivityClaim.create(
                student,
                activityType,
                policy,
                request.activityName(),
                request.activityDate(),
                request.requestedPoints(),
                request.detailData(),
                fileGroup
        );

        try {
            return MileageExternalActivityClaimResponse.from(
                    externalActivityClaimRepository.saveAndFlush(claim));
        } catch (DataIntegrityViolationException exception) {
            if (DbConstraintViolationMatcher.contains(
                    exception, EXTERNAL_CLAIM_FILE_GROUP_CONSTRAINT)) {
                throw new BusinessException(
                        ErrorCode.MILEAGE_ALREADY_CLAIMED,
                        "이미 다른 외부활동 신청에 사용된 증빙 파일입니다.");
            }
            throw exception;
        }
    }

    private MileagePolicy findApplicablePolicy(
            MileageActivityType activityType,
            LocalDate activityDate
    ) {
        List<MileagePolicy> policies = policyRepository.findActivePoliciesByActivityTypeOn(
                activityType.getActivityTypeId(), activityDate);
        MileagePolicy policy = policies.stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                        "활동 일자에 적용되는 마일리지 정책이 없습니다."));

        if (policy.getPoints() == null || policy.getPoints().signum() <= 0) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                    "활동 일자에 적용되는 유효한 마일리지 정책이 없습니다.");
        }
        return policy;
    }

    /** 프로그램 이수/역량진단 완료 전용 활동 유형을 학생의 외부활동 신청에 재사용하지 못하게 한다. */
    private void validateExternalActivityType(MileageActivityType activityType) {
        if (activityType.getProgramTypeCode() != null
                || ExtracurricularMileagePolicyDefinition.isProgramTypePolicy(activityType)
                || EXCLUDED_EARNING_ROUTES.stream()
                        .anyMatch(route -> route.equalsIgnoreCase(activityType.getEarningRoute()))
                || activityType.getCompetency() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "외부활동 신청에 사용할 수 없는 마일리지 활동 유형입니다.");
        }
    }

    private Set<String> uppercaseExcludedEarningRoutes() {
        return EXCLUDED_EARNING_ROUTES.stream()
                .map(route -> route.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 비교과 프로그램 첨부 연결 검증과 같은 기준으로 파일 그룹의 소유권·형식을 확인한다. */
    private FileGroup validateFileGroup(Integer fileGroupId, Integer uploaderId) {
        if (fileGroupId == null || fileGroupId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "증빙 파일 그룹 번호가 올바르지 않습니다.");
        }

        FileGroup fileGroup = fileGroupRepository.findById(fileGroupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "유효한 증빙 파일 그룹을 찾을 수 없습니다."));
        List<StoredFile> files = fileGroupService.getFiles(fileGroup);
        if (files.size() != 1 || !isPdf(files.get(0))) {
            throw new BusinessException(
                    ErrorCode.INVALID_FILE_TYPE,
                    "증빙 파일은 PDF 파일 1개만 첨부할 수 있습니다.");
        }
        if (!Objects.equals(files.get(0).getCreatedBy(), uploaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (externalActivityClaimRepository.existsByFileGroup_FileGroupId(fileGroupId)) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_ALREADY_CLAIMED,
                    "이미 다른 외부활동 신청에 사용된 증빙 파일입니다.");
        }
        return fileGroup;
    }

    private boolean isPdf(StoredFile file) {
        String originalFileName = file.getOriginalFileName();
        return originalFileName != null
                && originalFileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private void validateRequest(MileageExternalActivityClaimRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "외부활동 마일리지 신청 정보가 없습니다.");
        }
        if (request.activityTypeId() == null || request.activityTypeId() <= 0
                || request.fileGroupId() == null || request.fileGroupId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "외부활동 신청 식별자가 올바르지 않습니다.");
        }
        if (request.activityName() == null || request.activityName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "활동명을 입력해주세요.");
        }
        if (request.activityDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "활동 일자를 입력해주세요.");
        }
        BigDecimal requestedPoints = request.requestedPoints();
        if (requestedPoints != null && requestedPoints.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신청 마일리지는 0보다 커야 합니다.");
        }
    }

    private Integer requireUserId(Integer userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용자 정보가 올바르지 않습니다.");
        }
        return userId;
    }
}
