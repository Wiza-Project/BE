package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.helper.CareerBindingHelper;
import com.gnagnoohc.scms.domain.career.repository.JobPreferenceRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.DbConstraintViolationMatcher;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
 *       {@link JobPreference#update} 비즈니스 메서드로 Dirty Checking을 수행하고, 없으면 신규 생성({@code save})</li>
 *   <li><b>참조 무결성 검증:</b> NCS 직무 표준 및 근무지역 공통코드의 유효성을 사전 검증 후 엔티티에 바인딩</li>
 * </ul>
 *
 * @author YUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPreferenceService {

    private static final String UQ_STUDENT_PREFERENCE = "uq_student_job_preference";

    private final JobPreferenceRepository jobPreferenceRepository;
    private final AppUserRepository appUserRepository;
    private final CareerBindingHelper careerBindingHelper;

    /**
     * [학생] 본인의 등록된 취업 희망조건 단건을 조회
     *
     * @param studentUserId 대상 학생 사용자 계정 식별자 (PK)
     * @return 학생 취업 희망조건 상세 응답 DTO
     * @throws BusinessException 등록된 희망조건 데이터가 존재하지 않을 경우 ({@link ErrorCode#RESOURCE_NOT_FOUND})
     */
    public JobPreferenceResponseDTO getMyPreference(Integer studentUserId) {
        JobPreference preference = jobPreferenceRepository.findByStudent_UserId(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "등록된 희망조건이 없습니다."));

        return mapToResponseDTO(preference);
    }

    /**
     * [학생] 취업 희망조건을 등록하거나 기존 설정을 수정 (원자적 Upsert).
     *
     * @param studentUserId 대상 학생 사용자 계정 식별자 (PK)
     * @param requestDTO    희망 직무, 희망 지역, 고용형태, 희망 최소 연봉 정보가 담긴 요청 DTO
     * @return 등록 또는 수정이 완료된 학생 취업 희망조건 상세 응답 DTO
     * @throws BusinessException 학생 계정을 찾을 수 없거나 공통코드가 유효하지 않은 경우
     */
    @Transactional
    public JobPreferenceResponseDTO upsertPreference(Integer studentUserId, JobPreferenceRequestDTO requestDTO) {
        AppUser student = appUserRepository.findById(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        CommonCode ncsCode = careerBindingHelper.findValidCommonCodeOrNull(requestDTO.getNcsStandardId());
        CommonCode regionCode = careerBindingHelper.findValidRegionCodeOrNull(requestDTO.getPreferredRegionCodeId());

        JobPreference preference = jobPreferenceRepository.findByStudent_UserId(studentUserId)
                .map(existing -> {
                    existing.update(ncsCode, regionCode, requestDTO.getPreferredEmploymentType(), requestDTO.getMinimumSalary());
                    return existing;
                })
                .orElseGet(() -> {
                    try {
                        return jobPreferenceRepository.saveAndFlush(
                                JobPreference.builder()
                                        .student(student)
                                        .ncsCode(ncsCode)
                                        .regionCode(regionCode)
                                        .preferredEmploymentType(requestDTO.getPreferredEmploymentType())
                                        .minimumSalary(requestDTO.getMinimumSalary())
                                        .build()
                        );
                    } catch (DataIntegrityViolationException e) {
                        if (DbConstraintViolationMatcher.contains(e, UQ_STUDENT_PREFERENCE)) {
                            log.warn("[JobPreferenceService_DbConstraintViolationMatcher 제약조건 예외처리] 동시 생성 충돌 감지 -> 재조회 및 갱신 수행. studentUserId: {}", studentUserId);
                            JobPreference concurrentPref = jobPreferenceRepository.findByStudent_UserId(studentUserId)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "희망조건 저장 중 오류가 발생했습니다."));
                            concurrentPref.update(ncsCode, regionCode, requestDTO.getPreferredEmploymentType(), requestDTO.getMinimumSalary());
                            return concurrentPref;
                        }
                        throw e;
                    }
                });
        log.info("[JobPreferenceService] 학생 취업 희망조건 저장 완료. studentUserId: {}", studentUserId);
        return mapToResponseDTO(preference);
    }

    /**
     * 엔티티 객체를 클라이언트 반환용 응답 DTO로 매핑 변환
     * 시간 데이터는 공통 시간 유틸리티({@link DateTimeUtils})지정한 KST 오프셋 메소드를 호출-변환 처리
     *
     * @param jp 취업 희망조건 엔티티 원장
     * @return 변환된 취업 희망조건 Response DTO
     */
    private JobPreferenceResponseDTO mapToResponseDTO(JobPreference jp) {
        return JobPreferenceResponseDTO.builder()
                .jobPreferenceId(jp.getJobPreferenceId())
                .studentUserId(jp.getStudent().getUserId())
                .universityNo(jp.getStudent().getUniversityNo())
                .studentName(jp.getStudent().getUserName())
                .ncsStandardId(jp.getNcsCode() != null ? jp.getNcsCode().getCodeId() : null)
                .ncsJobName(jp.getNcsCode() != null ? jp.getNcsCode().getCodeName() : null)
                .preferredRegionCodeId(jp.getRegionCode() != null ? jp.getRegionCode().getCodeId() : null)
                .preferredRegionName(jp.getRegionCode() != null ? jp.getRegionCode().getCodeName() : null)
                .preferredEmploymentType(jp.getPreferredEmploymentType())
                .minimumSalary(jp.getMinimumSalary())
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(jp.getUpdatedAt()))
                .build();
    }
}