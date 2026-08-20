package com.gnagnoohc.scms.domain.career.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

/**
 * 교직원 전용 채용공고 검수(승인/반려) 처리 요청 DTO
 *
 * <p><strong>[보안 및 아키텍처 설계 유의사항]</strong></p>
 * <ul>
 *   <li><b>공고 식별자 (job_posting_id):</b> 요청 Body가 아닌 RESTful URL의 PathVariable로 전달받아 위변조 방지할 것</li>
 *   <li><b>검토자 식별자 (reviewed_by):</b> 클라이언트 입력 위조(위장) 방지 등 보안을 위해 DTO 필드에서 제외 처리.
 *       서버 인증 컨텍스트(Security / JWT 토큰)에서 로그인한 교직원 식별자를 직접 추출하여 주입 필요</li>
 *   <li><b>검토 일시 (reviewed_at):</b> 프론트엔드가 아닌 백엔드 서비스 계층에서 검토 시점 기준({@code OffsetDateTime.now()})으로 자동 기록</li>
 * </ul>
 *
 * @author YUN
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "교직원 담당자의 채용공고 검수 및 승인 요청 DTO")
public class JobPostingReviewRequestDTO {
    @NotBlank(message = "검수 상태값은 필수입니다.")
    @Schema(description = "검수 상태 (review_status: REQUESTED / APPROVED / REJECTED)", example = "APPROVED", allowableValues = {"REQUESTED", "APPROVED", "REJECTED"})
    private String reviewStatus;

    @Schema(description = "반려 사유 (rejection_reason, TEXT)", example = "급여 조건 및 공고 유형 불명확")
    private String rejectionReason;
}
