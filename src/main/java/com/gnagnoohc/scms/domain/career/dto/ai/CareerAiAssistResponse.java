package com.gnagnoohc.scms.domain.career.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI가 생성한 초안 응답. 저장은 기존 이력서·포트폴리오 저장 API가 담당하며, 이 응답 자체는 자동 저장되지 않는다. */
@Schema(description = "AI 초안 생성 응답 DTO. 화면에만 반영되며 자동 저장되지 않는다.")
public record CareerAiAssistResponse(
        @Schema(description = "생성된 초안 문장. 학생이 검토·수정한 뒤 저장 API로 직접 저장해야 한다.")
        String content
) {
}
