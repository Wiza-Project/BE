package com.gnagnoohc.scms.domain.academic.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 교직원 학생 목록(GET /api/admin/students) 다중 조건 검색 파라미터 DTO.
 * {@link com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSearchConditionDTO}와
 * 같은 관례 — 값이 없는 필드는 QueryDSL 동적 조건에서 무시 처리된다.
 */
@Getter
@Setter
@Schema(description = "교직원 학생 목록 다중 조건 검색 파라미터")
public class AdminStudentSearchConditionDTO {

    @Schema(description = "소속학과 공통코드 식별자(MAJOR 그룹, common_code.code_id)", example = "8000")
    private Integer majorCodeId;

    @Schema(description = "학년(1~4)", example = "3")
    private Integer grade;

    @Schema(description = "학적상태 — 재학/휴학/졸업/제적/자퇴", example = "재학")
    private String status;

    @Schema(description = "학번 또는 이름 부분 일치 검색어", example = "홍길동")
    private String keyword;
}
