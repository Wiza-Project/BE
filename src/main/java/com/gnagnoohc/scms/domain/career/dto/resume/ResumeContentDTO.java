package com.gnagnoohc.scms.domain.career.dto.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * 고정 이력서 템플릿의 본문. 항목별로 구조화된 필드는 검증하고,
 * 템플릿에 없는 값은 extra에 JSON으로 담는다.
 */
@Schema(description = "이력서 템플릿 본문")
public record ResumeContentDTO(

        @Valid
        ResumeContactDTO contact,

        @Valid
        List<@Valid ResumeEducationDTO> educations,

        @Valid
        List<@Valid ResumeCareerDTO> careers,

        @Valid
        List<@Valid ResumeCertificationDTO> certifications,

        @Valid
        List<@Valid ResumeLanguageTestDTO> languageTests,

        @Schema(description = "고정 템플릿에 없는 확장 입력값")
        Map<String, Object> extra
) {
}
