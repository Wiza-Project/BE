package com.gnagnoohc.scms.domain.user.service.consent;

import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;

/**
 * ConsentPolicy.moduleCode 컬럼에 저장되는 모듈 분류 코드 enum.
 */
public enum ConsentModuleCode {
    /** 서비스 공통 */
    COMMON,

    /** 핵심역량진단 */
    ASSESSMENT,

    /** 상담 */
    COUNSELING,

    /** 취업·창업 */
    CAREER,

    /** 비교과 프로그램 */
    PROGRAM;

    /**
     * 문자열 파라미터를 ConsentModuleCode Enum으로 변환합니다.
     * @throws BusinessException INVALID_INPUT - null이거나 유효하지 않은 모듈 코드인 경우
     */
    public static ConsentModuleCode from(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}