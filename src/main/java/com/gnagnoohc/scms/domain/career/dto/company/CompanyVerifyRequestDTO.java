package com.gnagnoohc.scms.domain.career.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 교직원(취창업지원과 D400) 전용 기업 승인/반려 심사 요청 DTO
 *
 * <p>신청 접수된 PENDING 상태의 기업에 대해 사업자등록 진위 여부를 확인한 후
 * 승인(VERIFIED) 또는 반려(REJECTED) 상태로 전환</p>
 *
 * <ul>
 *   <li>{@code verificationStatus}: 승인('VERIFIED') 또는 반려('REJECTED')</li>
 *   <li>{@code rejectReason}: 반려 시 사유 기록 (엔티티 미저장, 감사 로그용)</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "교직원 전용 기업 승인/반려 심사 요청 DTO")
public class CompanyVerifyRequestDTO {

    @NotBlank(message = "승인 상태값은 필수입니다.")
    @Pattern(regexp = "^(VERIFIED|REJECTED)$", message = "검증 상태는 VERIFIED 또는 REJECTED만 가능합니다.")
    @Schema(description = "기업 검증 상태", allowableValues = {"VERIFIED", "REJECTED"}, example = "VERIFIED")
    private String verificationStatus;

    @Schema(description = "반려 사유 (반려 시 필수 기재 권장)", example = "사업자등록번호 불일치 및 확인 불가")
    private String rejectReason;
}