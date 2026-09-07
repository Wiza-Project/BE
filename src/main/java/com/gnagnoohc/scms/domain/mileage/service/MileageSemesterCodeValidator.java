package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** 마일리지 도메인 전반에서 사용하는 semesterCode 정규화와 유효성 검증을 담당한다. */
@Component
@RequiredArgsConstructor
public class MileageSemesterCodeValidator {

    private static final String ALL_SEMESTER_CODE = "ALL";
    private static final String SEMESTER_CODE_GROUP = "SEMESTER";

    private final CommonCodeRepository commonCodeRepository;

    /** 대소문자/공백 차이 없이 비교할 수 있도록 표준화한다. null은 빈 문자열로 취급한다(검증하지 않음). */
    public String normalize(String semesterCode) {
        return semesterCode == null ? "" : semesterCode.trim().toUpperCase(Locale.ROOT);
    }

    /** semesterCode가 필수인 호출용: null/blank, ALL, 비활성/미존재 코드를 모두 거부한다. */
    public String requireSemesterCode(String semesterCode) {
        if (semesterCode == null || semesterCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기 정보가 올바르지 않습니다.");
        }
        return normalizeAndValidate(semesterCode);
    }

    /** semesterCode가 선택(필터 없음=null 허용)인 호출용. */
    public String normalizeSemesterCodeIfPresent(String semesterCode) {
        if (semesterCode == null) {
            return null;
        }
        if (semesterCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기 정보가 올바르지 않습니다.");
        }
        return normalizeAndValidate(semesterCode);
    }

    /** semesterCode 생략 시 ALL(전체 학기 공통)로 대체하는 등록/수정용. ALL을 유효한 값으로 허용한다. */
    public String resolveSemesterCodeOrDefault(String semesterCode) {
        if (semesterCode == null || semesterCode.isBlank()) {
            return ALL_SEMESTER_CODE;
        }
        String normalized = normalize(semesterCode);
        if (!ALL_SEMESTER_CODE.equals(normalized) && !isActiveSemesterCode(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 학기 코드입니다.");
        }
        return normalized;
    }

    private String normalizeAndValidate(String semesterCode) {
        String normalized = normalize(semesterCode);
        if (ALL_SEMESTER_CODE.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기는 개별 학기로 지정해야 합니다.");
        }
        if (!isActiveSemesterCode(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 학기 코드입니다.");
        }
        return normalized;
    }

    private boolean isActiveSemesterCode(String semesterCode) {
        return commonCodeRepository.findByCodeGroupAndCode(SEMESTER_CODE_GROUP, semesterCode)
                .filter(CommonCode::isActive)
                .isPresent();
    }
}
