package com.gnagnoohc.scms.domain.career.dto.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 관심 공고 스크랩 토글 결과 응답 DTO
 *
 * <p><strong>[판정 기준]</strong></p>
 * <ul>
 *   <li>{@code bookmarked_at} 컬럼이 존재하면 {@code isScrapped = true}, null이면 {@code isScrapped = false}</li>
 *   <li>{@code isScrapped}는 가상의 플래그지만, 실제 데베로는 북마크일시(시각)으로 저장 + 채용공고의 DTO에서 다대다 로직 구현 완료</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Builder
@Schema(description = "관심 공고 스크랩 토글 결과 응답 DTO")
public class JobScrapToggleResponseDTO {

    @Schema(description = "채용공고 식별자 (job_posting_id)", example = "1")
    private Integer jobPostingId;

    @Schema(description = "스크랩 여부 (true: 저장됨, false: 해제됨)", example = "true")
    private Boolean isScrapped;

    @Schema(description = "스크랩 처리 일시 (bookmarked_at, KST)", example = "2026-09-01T10:00:00+09:00")
    private OffsetDateTime bookmarkedAt;
}