package com.gnagnoohc.scms.domain.career.helper;

import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.helper.CommonCodeLookupHelper;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 취창업 도메인 엔티티의 공통코드(NCS 직무, 근무 지역) 참조 검증 및 바인딩 헬퍼.
 */
@Component
@RequiredArgsConstructor
public class CareerBindingHelper {

    private static final String GROUP_REGION_CODE = "REGION_CODE";

    private final CommonCodeRepository commonCodeRepository;
    private final CommonCodeLookupHelper commonCodeLookupHelper;

    /**
     * 공통코드 식별자(PK)로 단건 조회 및 활성 상태(active = true)를 확인하여 반환합니다.
     * NCS 직무 및 공통 참조에 사용됩니다.
     */
    public CommonCode findValidCommonCodeOrNull(Integer codeId) {
        if (codeId == null) {
            return null;
        }
        return commonCodeRepository.findById(codeId)
                .filter(CommonCode::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효한 공통코드 정보를 찾을 수 없습니다."));
    }

    /**
     * 근무 지역 공통코드 식별자(PK)를 조회하고 REGION_CODE 그룹에 속하는지 검증 후 반환합니다.
     */
    public CommonCode findValidRegionCodeOrNull(Integer regionCodeId) {
        if (regionCodeId == null) {
            return null;
        }
        return commonCodeRepository.findById(regionCodeId)
                .filter(CommonCode::isActive)
                .filter(code -> GROUP_REGION_CODE.equals(code.getCodeGroup()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효한 근무지역 코드를 찾을 수 없습니다."));
    }

    /**
     * 특정 코드 그룹 및 코드값의 활성화 여부를 1차 검증합니다.
     */
    public void validateActiveGroupCode(String codeGroup, String code) {
        commonCodeLookupHelper.validateActiveCode(codeGroup, code);
    }
}