package com.gnagnoohc.scms.domain.career.dto.preference;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 학생 취업 희망조건 등록 및 수정 요청 DTO
 *
 * <p><strong>[데이터 바인딩 및 유효성 검증 규칙]</strong></p>
 * <ul>
 *   <li><b>인증 처리:</b> 학생 식별자(studentId)는 Body로 받지 않고 SecurityContext({@code AuthUser})에서 직접 바인딩</li>
 *   <li><b>ncsStandardId:</b> 선호 직무 분류 식별자 (선택 입력)</li>
 *   <li><b>preferredRegionCodeId:</b> 선호 지역 공통코드 식별자 (선택 입력)</li>
 *   <li><b>minimumSalary:</b> 음수 입력을 방지하기 위해 {@code @PositiveOrZero} 검증 수행</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@NoArgsConstructor
@Schema(description = "학생 취업 희망조건 등록/수정 요청 DTO")
public class JobPreferenceRequestDTO {

    @Schema(description = "NCS 직무 표준 식별자 (ncs_standard_id)", example = "101")
    private Integer ncsStandardId;

    @Schema(description = "희망 근무 지역 공통코드 식별자 (code_id)", example = "201")
    private Integer preferredRegionCodeId;

    @Schema(description = "희망 고용형태 (REGULAR, CONTRACT, INTERN 등)", example = "REGULAR")
    private String preferredEmploymentType;

    @PositiveOrZero(message = "희망 최소 연봉은 0 이상이어야 합니다.")
    @Schema(description = "희망 최소 연봉 (만원 단위 또는 원 단위)", example = "35000000.00")
    private BigDecimal minimumSalary;
}