package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceResponseDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.helper.CareerBindingHelper;
import com.gnagnoohc.scms.domain.career.repository.JobPreferenceRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.helper.JdbcUpsertHelper;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

    private final JobPreferenceRepository jobPreferenceRepository;
    private final AppUserRepository appUserRepository;
    private final CareerBindingHelper careerBindingHelper;
    private final JdbcUpsertHelper jdbcUpsertHelper;

    // 학생 희망 조건값 벡터화 처리를 위한 서비스단 주입
    private final StudentProfileService studentProfileService;

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
     * <p><strong>[비즈니스 로직 처리 순서]</strong></p>
     * <ul>
     *   <li>1. 대상 학생 및 공통코드(NCS 직무, 근무지역) 유효성 검증</li>
     *   <li>2. {@link JdbcUpsertHelper}를 통해 초기 row를 원자적으로 확보 ({@code ON CONFLICT DO NOTHING})</li>
     *   <li>3. 영속 엔티티 조회 후 {@link JobPreference#update} 비즈니스 메서드로 최종 값 갱신 (Dirty Checking)</li>
     * </ul>
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

        // PostgreSQL ON CONFLICT DO NOTHING을 통한 원자적 초기 row 확보 (JPA 트랜잭션 롤백 오염 방지_코드래빗 리뷰 적용)
        Instant now = Instant.now();
        String sql = "INSERT INTO job_preference (student_id, ncs_code_id, preferred_region_code_id, preferred_employment_type, minimum_salary, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (student_id) DO NOTHING";

        Integer ncsCodeId = ncsCode != null ? ncsCode.getCodeId() : null;
        Integer regionCodeId = regionCode != null ? regionCode.getCodeId() : null;

        jdbcUpsertHelper.executeInsertDoNothing(
                sql,
                student.getUserId(),
                ncsCodeId,
                regionCodeId,
                requestDTO.getPreferredEmploymentType(),
                requestDTO.getMinimumSalary(),
                now,
                now
        );

        // 엔티티 재조회 후 최신 요청 데이터로 갱신 (더티 체킹)
        JobPreference preference = jobPreferenceRepository.findByStudent_UserId(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "희망조건 저장 중 오류가 발생했습니다."));

        preference.update(ncsCode, regionCode, requestDTO.getPreferredEmploymentType(), requestDTO.getMinimumSalary());
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