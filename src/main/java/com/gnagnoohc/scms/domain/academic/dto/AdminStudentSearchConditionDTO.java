package com.gnagnoohc.scms.domain.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 교직원 학생 목록(GET /api/admin/students) 다중 조건 검색 파라미터 DTO.
 * {@link com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSearchConditionDTO}와
 * 같은 관례 — 값이 없는 필드는 QueryDSL 동적 조건에서 무시 처리된다.
 *
 * <p>{@code grade}만 예외적으로 {@code @Min}/{@code @Max}가 있다 — DB 컬럼이
 * {@code SMALLINT}라 범위 밖 값을 {@code shortValue()}로 좁히면 조용히 다른 값으로
 * 둔갑한다(PR #60 리뷰). 다른 검색 조건 필드들엔 이런 narrowing 변환이 없어 이 필드만
 * 검증을 건다 — 전체 DTO에 검증을 일괄 추가하는 건 아니다.</p>
 */
@Getter
@Setter
@Schema(description = "교직원 학생 목록 다중 조건 검색 파라미터")
public class AdminStudentSearchConditionDTO {

    @Schema(description = "소속학과 공통코드 식별자(MAJOR 그룹, common_code.code_id)", example = "8000")
    private Integer majorCodeId;

    @Min(value = 1, message = "학년은 1~4 사이여야 합니다.")
    @Max(value = 4, message = "학년은 1~4 사이여야 합니다.")
    @Schema(description = "학년(1~4)", example = "3")
    private Integer grade;

    @Schema(description = "학적상태 — 재학/휴학/졸업/제적/자퇴", example = "재학")
    private String status;

    @Schema(description = "학번 또는 이름 부분 일치 검색어", example = "홍길동")
    private String keyword;
}
