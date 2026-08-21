package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
import com.gnagnoohc.scms.domain.career.repository.JobPreferenceRepository;
import com.gnagnoohc.scms.domain.career.repository.NcsStandardRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 학생 취업 희망조건 핵심 비즈니스 로직 서비스
 *
 * <p><strong>[아키텍처 가이드라인 및 처리 원칙]</strong></p>
 * <ul>
 *   <li><b>Upsert 트랜잭션 패턴:</b> 기존 등록 이력 유무를 판별하여 데이터가 존재할 경우
 *       {@link JobPreference#update} 비즈니스 메서드로 Dirty Checking을 수행하고, 없으면 신규 생성({@code save})합니다.</li>
 *   <li><b>참조 무결성 검증:</b> NCS 직무 표준 및 근무지역 공통코드의 유효성을 사전 검증 후 엔티티에 바인딩합니다.</li>
 * </ul>
 *
 * @author YUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPreferenceService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private final JobPreferenceRepository jobPreferenceRepository;
    private final AppUserRepository appUserRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final NcsStandardRepository ncsStandardRepository;

    /**
     * [학생] 내 취업 희망조건 단건 조회
     */
    public JobPreferenceResponseDTO getMyPreference(Integer studentUserId) {
        JobPreference preference = jobPreferenceRepository.findByStudent_UserId(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "등록된 희망조건이 없습니다."));

        return mapToResponseDTO(preference);
    }

    /**
     * [학생] 취업 희망조건 등록 및 수정 (Upsert)
     */
    @Transactional
    public JobPreferenceResponseDTO upsertPreference(Integer studentUserId, JobPreferenceRequestDTO requestDTO) {
        AppUser student = appUserRepository.findById(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        NcsStandard ncsStandard = (requestDTO.getNcsStandardId() != null)
                ? ncsStandardRepository.findById(requestDTO.getNcsStandardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "해당 NCS 직무 표준 정보를 찾을 수 없습니다."))
                : null;

        CommonCode regionCode = (requestDTO.getPreferredRegionCodeId() != null)
                ? commonCodeRepository.findById(requestDTO.getPreferredRegionCodeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "해당 지역 공통코드를 찾을 수 없습니다."))
                : null;

        JobPreference preference = jobPreferenceRepository.findByStudent_UserId(studentUserId)
                .map(existing -> {
                    existing.update(ncsStandard, regionCode, requestDTO.getPreferredEmploymentType(), requestDTO.getMinimumSalary());
                    return existing;
                })
                .orElseGet(() -> jobPreferenceRepository.save(
                        JobPreference.builder()
                                .student(student)
                                .ncsStandard(ncsStandard)
                                .preferredRegionCode(regionCode)
                                .preferredEmploymentType(requestDTO.getPreferredEmploymentType())
                                .minimumSalary(requestDTO.getMinimumSalary())
                                .build()
                ));

        log.info("[JobPreferenceService] 학생 취업 희망조건 저장 완료. studentUserId: {}", studentUserId);
        return mapToResponseDTO(preference);
    }

    private JobPreferenceResponseDTO mapToResponseDTO(JobPreference jp) {
        return JobPreferenceResponseDTO.builder()
                .jobPreferenceId(jp.getJobPreferenceId())
                .studentUserId(jp.getStudent().getUserId())
                .universityNo(jp.getStudent().getUniversityNo())
                .studentName(jp.getStudent().getUserName())
                .ncsStandardId(jp.getNcsStandard() != null ? jp.getNcsStandard().getNcsStandardId() : null)
                .ncsJobName(jp.getNcsStandard() != null ? jp.getNcsStandard().getCategoryName() : null)
                .preferredRegionCodeId(jp.getPreferredRegionCode() != null ? jp.getPreferredRegionCode().getCodeId() : null)
                .preferredRegionName(jp.getPreferredRegionCode() != null ? jp.getPreferredRegionCode().getCodeName() : null)
                .preferredEmploymentType(jp.getPreferredEmploymentType())
                .minimumSalary(jp.getMinimumSalary())
                .updatedAt(toOffsetDateTime(jp.getUpdatedAt()))
                .build();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return (instant == null) ? null : instant.atZone(KST_ZONE).toOffsetDateTime();
    }
}