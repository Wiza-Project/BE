package com.gnagnoohc.scms.domain.program.dto.request;

// 유효성 검사(Validation) 어노테이션들을 사용하기 위해 import 한다.
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
 * 비교과프로그램 "수정" 요청 DTO. record 키워드로 만들면 필드값이 바뀌지 않는(불변) 데이터 객체가 자동으로 생성된다.
 * PUT 방식이라 등록(register) 때처럼 전체 필드를 한 번에 다시 받는다(일부 필드만 보내는 PATCH 방식이 아님).
 *
 * 아래 필드에는 일부러 넣지 않은 값들이 있다.
 *   - programId(수정 대상 PK)      : 요청 바디가 아니라 URL 경로(/programs/{programId})로 받는다.
 *   - managerUserId(등록자)        : 이 API로 등록자(소유권)를 바꿀 수 없게 하기 위해 아예 요청 항목에서 뺐다.
 *   - programStatus                : 상태값은 별도의 "상태 변경" 기능에서 다루도록 하고, 이 수정 API에서는 손대지 않는다.
 *
 * fileGroupId를 비워서(null) 보내면 기존 첨부파일이 그대로 유지된다(record 특성상 "키 생략"과
 * "명시적 null"을 구분할 수 없기 때문). 첨부파일을 삭제하려면 fileGroupId는 비워두고
 * clearFileGroup=true를 명시적으로 보내야 한다(MileagePolicyUpdateRequestDTO의 clearValidTo와 동일한 패턴).
 */
public record ProgramUpdateRequestDTO(
        // 첨부파일 그룹 id. 파일이 없을 수도 있으므로 @NotNull을 붙이지 않았다(선택값).
        Integer fileGroupId,

        // 위 fileGroupId를 비워두고 이 값을 true로 보내면 기존 첨부파일 연결을 해제(삭제)한다.
        boolean clearFileGroup,

        // 운영 단위(부서) 코드 id. 프론트가 드롭다운으로 선택해서 보내야 하는 필수값.
        @NotNull Integer operatingUnitCodeId,

        // 프로그램 유형(분류) 코드 id. 마찬가지로 필수값.
        @NotNull Integer programTypeCodeId,

        // 이 프로그램과 연결된 핵심역량 id. 필수값.
        @NotNull Integer competencyId,

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
