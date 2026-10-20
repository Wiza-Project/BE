package com.gnagnoohc.scms.domain.career.dto.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온라인 채용공고 지원 신청 요청 DTO
 *
 * <p><strong>[데이터 매핑 및 비즈니스 검증 기준]</strong></p>
 * <ul>
 *   <li><b>jobPostingId:</b> 지원 대상 채용공고 식별자 (job_posting_id, 필수)</li>
 *   <li><b>careerDocumentId:</b> 제출할 진로문서(이력서/포트폴리오) 식별자 (선택 연동)</li>
 *   <li><b>isThirdPartyConsent:</b> 기업 채용 지원을 위한 개인정보 제3자 제공 필수 동의 (@AssertTrue 검증)</li>
 *   <li><b>인증 처리:</b> 지원 학생 식별자(userId)는 본 Body가 아닌 SecurityContext(AuthUser)에서 주입받아 처리</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "온라인 채용공고 지원 신청 요청 DTO")
public class JobRelationRequestDTO {

    @NotNull(message = "채용공고 식별자는 필수입니다.")
    @Schema(description = "채용공고 식별자 (job_posting_id)", example = "1")
    private Integer jobPostingId;

    @Schema(description = "제출할 진로문서(이력서) 식별자 (career_document_id)", example = "10")
    private Integer careerDocumentId;

    @NotNull(message = "개인정보 제3자 제공 동의 여부는 필수입니다.")
    @AssertTrue(message = "채용 지원을 위해 개인정보 제3자 제공 동의가 필수입니다.")
    @Schema(description = "개인정보 제3자 제공 동의 여부 (반드시 true)", example = "true")
    private Boolean isThirdPartyConsent;
}