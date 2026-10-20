package com.gnagnoohc.scms.domain.career.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포트폴리오 공개 여부 변경 요청 DTO
 */
@Getter
@NoArgsConstructor
@Schema(description = "포트폴리오 공개 여부 변경 요청 DTO")
public class PortfolioVisibilityRequestDTO {

    @NotNull(message = "공개 여부 값은 필수입니다.")
    @Schema(description = "공개 여부", example = "true")
    private Boolean isPublic;
}
