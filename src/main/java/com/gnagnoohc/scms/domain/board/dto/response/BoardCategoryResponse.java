package com.gnagnoohc.scms.domain.board.dto.response;

import com.gnagnoohc.scms.global.common.entity.CommonCode;

/** FAQ 카테고리. common_code(code_group='FAQ_CATEGORY') 행을 그대로 노출한다. */
public record BoardCategoryResponse(
        String categoryCode,
        String categoryName,
        Integer displayOrder,
        boolean active
) {
    public static BoardCategoryResponse from(CommonCode code) {
        return new BoardCategoryResponse(code.getCode(), code.getCodeName(), code.getSortOrder(), code.isActive());
    }
}
