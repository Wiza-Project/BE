package com.gnagnoohc.scms.domain.program.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 금액/소수처럼 정확한 계산이 필요한 값(completionRate)을 담기 위한 타입.
import java.math.BigDecimal;
// 날짜+시간+시간대를 함께 표현하는 타입(모집/운영 시작·종료 시각에 사용).
import java.time.Instant;

/**
 * 비교과프로그램 "등록" 요청 DTO. record 키워드로 만들면 필드값이 바뀌지 않는(불변) 데이터 객체가 자동으로 생성된다.
 * 클라이언트(프론트엔드)가 보내는 입력값만 여기에 담는다.
 *
 * 아래 필드에는 일부러 넣지 않은 값들이 있다.
 *   - managerUserId(등록 담당자) : 클라이언트가 마음대로 "이 사람이 등록한 걸로 해줘"라고 위조하지 못하도록,
 *                                요청 항목에서 빼고 서버가 로그인한 사용자(authUser)의 id로 직접 채운다.
 *   - programStatus            : 새로 등록되는 프로그램은 항상 "모집중" 상태로 고정되므로 클라이언트 입력을 받지 않는다.
 */
public record ProgramRegisterRequestDTO(
        // 첨부파일 그룹 id. 포스터 등 이미지가 없을 수도 있으므로 @NotNull을 붙이지 않았다(선택값).
        Integer fileGroupId,

        // 운영 단위(부서) 코드 id. 프론트가 드롭다운으로 선택해서 보내야 하는 필수값.
        @NotNull Integer operatingUnitCodeId,

        // 프로그램 유형(분류) 코드 id. 프론트가 드롭다운으로 선택해서 보내야 하는 필수값.
        @NotNull Integer programTypeCodeId,

        // 이 프로그램과 연결된 핵심역량 id. 필수값.
        @NotNull Integer competencyId,

        // 마일리지 정책 id. 정책이 없는 프로그램도 있을 수 있으므로 선택값(널 허용).
        Integer mileagePolicyId,

        /**
         * 프로그램명. @NotBlank는 null/빈 문자열/공백만 있는 문자열을 모두 막고,
         * @Size(max=200)은 엔티티 컬럼 길이(200자)를 넘지 않도록 미리 검증한다.
         */
        @NotBlank @Size(max = 200) String programName,

        // 프로그램 설명. 길이 제한이 없는 text 컬럼이라 별도 검증 없이 그대로 받는다.
        String description,

        // 모집 시작 시각. 반드시 있어야 하므로 @NotNull.
        @NotNull Instant recruitmentStartsAt,

        /**
         * 모집 종료 시각. 반드시 있어야 하므로 @NotNull.
         * (모집 시작 < 모집 종료 같은 "값들 사이의" 논리 검증은 어노테이션만으로는 못 하므로 서비스 계층에서 별도로 검사한다.)
         */
        @NotNull Instant recruitmentEndsAt,

        // 운영 시작 시각. 필수값.
        @NotNull Instant operationStartsAt,

        // 운영 종료 시각. 필수값.
        @NotNull Instant operationEndsAt,

        // 정원. @NotNull(필수) + @Positive(1 이상의 양수만 허용, 0이나 음수 불가).
        @NotNull @Positive Integer capacity,

        // 이수 기준 출석률(%). 0~100 사이 값만 허용. 요청에 안 담겨오면(null) 서비스에서 기본값(80)으로 채운다.
        @DecimalMin("0") @DecimalMax("100") BigDecimal completionRate
) {
}
