package com.gnagnoohc.scms.domain.mileage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyUpdateRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileagePolicyResponseDTO;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageActivityTypeRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.DbConstraintViolationMatcher;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class MileagePolicyService {

    // 학기 코드를 생략한 등록 요청에 채워 넣는 기본값. "전학기 공통" 정책을 의미한다(MileagePolicy 엔티티 기본값과 동일).
    private static final String DEFAULT_SEMESTER_CODE = "ALL";
    private static final String INITIAL_STATUS = "ACTIVE";

    private final MileagePolicyRepository policyRepository;
    private final MileageActivityTypeRepository activityTypeRepository;

    /**
     * ── "등록(Create)" 기능 ──────────────────────────────────────────────
     *
     * request      : 등록할 정책 내용 (요청 바디에서 옴)
     * staffId      : 지금 로그인해서 이 요청을 보낸 교직원의 id (인증 정보에서 옴, 클라이언트가 위조 불가) → created_by로 사용
     *
     * version_no는 클라이언트가 정하지 않는다 — 같은 활동유형+학기 조합 내에서 서버가 자동으로 다음 버전을 채번한다.
     */
    public MileagePolicyResponseDTO register(MileagePolicyRegisterRequestDTO request, Integer staffId) {
        MileageActivityType activityType = activityTypeRepository.findByIdForUpdate(request.activityTypeId())
                .filter(MileageActivityType::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.MILEAGE_ACTIVITY_TYPE_NOT_FOUND));

        String semesterCode = resolveSemesterCode(request.semesterCode());
        validatePeriod(request.validFrom(), request.validTo());
        validatePoints(request.points());

        Integer nextVersionNo = policyRepository.findNextVersionNo(
                activityType.getActivityTypeId(), semesterCode);

        Instant now = Instant.now();
        Integer mileagePolicyId;
        try {
            mileagePolicyId = policyRepository.insertPolicy(
                activityType.getActivityTypeId(),
                    semesterCode,
                    nextVersionNo,
                    request.points(),
                    request.maximumPoints(),
                    request.validFrom(),
                    request.validTo(),
                    writeJson(request.duplicateRule()),
                    INITIAL_STATUS,
                    staffId,
                    now
            );
        } catch (DataIntegrityViolationException e) {
            // findByIdForUpdate 락으로 동시 등록 채번 레이스는 막았지만, 방어적으로 유니크 제약 위반도 처리한다.
            if (DbConstraintViolationMatcher.contains(e, "uq_mileage_policy_activity_period_version")) {
                throw new BusinessException(ErrorCode.MILEAGE_POLICY_DUPLICATE);
            }
            throw e;
        }

        return getDetail(mileagePolicyId);
    }

    @Transactional(readOnly = true)
    public PageResponse<MileagePolicyResponseDTO> list(Integer activityTypeId,
                                                         String semesterCode, String policyStatus,
                                                         Pageable pageable) {
        Specification<MileagePolicy> spec = buildFilter(activityTypeId, semesterCode, policyStatus);
        Page<MileagePolicy> page = policyRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(MileagePolicyResponseDTO::from));
    }

    @Transactional(readOnly = true)
    public MileagePolicyResponseDTO getDetail(Integer mileagePolicyId) {
        return policyRepository.findById(mileagePolicyId)
                .map(MileagePolicyResponseDTO::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND));
    }

    /**
     * ── "수정(Update)" 기능 ──────────────────────────────────────────────
     *
     * 활동유형/학기/버전(정책의 식별 필드)은 바꿀 수 없고, 그 외 필드만 부분 수정한다.
     * 요청 필드가 null이면 기존 값을 그대로 유지한다.
     */
    public MileagePolicyResponseDTO update(Integer mileagePolicyId, MileagePolicyUpdateRequestDTO request) {
        if (request.clearValidTo() && request.validTo() != null) {
            throw new BusinessException(ErrorCode.MILEAGE_POLICY_VALID_TO_CONFLICT);
        }

        MileagePolicy policy = policyRepository.findByIdForUpdate(mileagePolicyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND));

        BigDecimal points = request.points() != null ? request.points() : policy.getPoints();
        BigDecimal maximumPoints = request.maximumPoints() != null ? request.maximumPoints() : policy.getMaximumPoints();
        LocalDate validFrom = request.validFrom() != null ? request.validFrom() : policy.getValidFrom();
        LocalDate validTo = request.clearValidTo() ? null
                : request.validTo() != null ? request.validTo() : policy.getValidTo();
        JsonNode duplicateRule = request.duplicateRule() != null ? request.duplicateRule() : policy.getDuplicateRule();
        String policyStatus = request.policyStatus() != null ? request.policyStatus() : policy.getPolicyStatus();

        validatePeriod(validFrom, validTo);
        validatePoints(points);

        int updatedRows = policyRepository.updatePolicy(
                mileagePolicyId, points, maximumPoints, validFrom, validTo, writeJson(duplicateRule), policyStatus);
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND);
        }

        return getDetail(mileagePolicyId);
    }

    private String resolveSemesterCode(String semesterCode) {
        return (semesterCode == null || semesterCode.isBlank()) ? DEFAULT_SEMESTER_CODE : semesterCode;
    }

    // 적용 종료일(validTo)은 nullable(무기한)이라 있을 때만 검사한다.
    private void validatePeriod(LocalDate validFrom, LocalDate validTo) {
        if (validTo != null && !validFrom.isBefore(validTo)) {
            throw new BusinessException(ErrorCode.MILEAGE_POLICY_INVALID_PERIOD);
        }
    }

    private void validatePoints(BigDecimal points) {
        if (points == null || points.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "마일리지 정책의 포인트는 0보다 커야 합니다.");
        }
    }

    // duplicate_rule은 jsonb 컬럼이라 native 쿼리로 넘기기 전에 문자열로 직렬화한다(레포지토리의 CAST(:duplicateRule AS jsonb) 참고).
    // JsonNode.toString()은 이미 파싱된 노드를 그대로 JSON 텍스트로 직렬화하므로 별도 ObjectMapper 빈이 필요 없다.
    private String writeJson(JsonNode node) {
        return node == null ? null : node.toString();
    }

    private Specification<MileagePolicy> buildFilter(Integer activityTypeId,
                                                       String semesterCode, String policyStatus) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (activityTypeId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("activityType").get("activityTypeId"), activityTypeId));
            }
            if (semesterCode != null) {
                predicate = cb.and(predicate, cb.equal(root.get("semesterCode"), semesterCode));
            }
            if (policyStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("policyStatus"), policyStatus));
            }
            return predicate;
        };
    }
}
