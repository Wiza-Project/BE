package com.gnagnoohc.scms.global.common.dto;

import com.gnagnoohc.scms.global.common.entity.CommonCode;

// 공통코드 조회 응답 DTO. 부모 코드는 있으면 ID만 내려준다
public record CommonCodeResponseDTO(
        Integer codeId,
        Integer parentCodeId,
        String codeGroup,
        String code,
        String codeName,
        String description,
        Integer sortOrder
) {
    public static CommonCodeResponseDTO from(CommonCode commonCode) {
        return new CommonCodeResponseDTO(
                commonCode.getCodeId(),
                commonCode.getParentCode() == null ? null : commonCode.getParentCode().getCodeId(),
                commonCode.getCodeGroup(),
                commonCode.getCode(),
                commonCode.getCodeName(),
                commonCode.getDescription(),
                commonCode.getSortOrder()
        );
    }
}
