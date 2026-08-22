package com.gnagnoohc.scms.domain.career.dto.preference;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 학생 취업 희망조건 단건 조회 응답 DTO
 *
 * <p><strong>[Entity 매핑 및 데이터 반환 기준]</strong></p>
 * <ul>
 *   <li>{@code JobPreference} 원장 및 연관 {@code AppUser}, {@code NcsStandard}, {@code CommonCode} 데이터를 조립 반환한다.</li>
 *   <li>NCS 직무 및 지역 코드가 미설정 상태일 경우 관련 ID 및 명칭 필드는 {@code null}을 반환한다 (Null-Safe 보장).</li>
 *   <li>시간 데이터는 ISO-8601 KST({@code OffsetDateTime}) 포맷으로 표준화하여 제공한다.</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Builder
@Schema(description = "학생 취업 희망조건 조회 응답 DTO")
public class JobPreferenceResponseDTO {

    @Schema(description = "희망조건 식별자 PK", example = "1")
    private Integer jobPreferenceId;

    @Schema(description = "학생 식별자", example = "10")
    private Integer studentUserId;

    @Schema(description = "학생 학번", example = "20240001")
    private String universityNo;

    @Schema(description = "학생 이름", example = "홍길동")
    private String studentName;

    @Schema(description = "NCS 직무 표준 식별자", example = "101")
    private Integer ncsStandardId;

    @Schema(description = "NCS 직무 분류명 (소분류/세분류)", example = "응용SW엔지니어링")
    private String ncsJobName;

    @Schema(description = "희망 근무 지역 코드 식별자", example = "201")
    private Integer preferredRegionCodeId;

    @Schema(description = "희망 근무 지역명", example = "서울특별시 강남구")
    private String preferredRegionName;

    @Schema(description = "희망 고용형태", example = "REGULAR")
    private String preferredEmploymentType;

    @Schema(description = "희망 최소 연봉", example = "35000000.00")
    private BigDecimal minimumSalary;

    @Schema(description = "최종 수정 일시 (KST)")
    private OffsetDateTime updatedAt;
}