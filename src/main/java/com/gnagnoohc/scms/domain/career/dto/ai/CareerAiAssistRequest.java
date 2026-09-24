package com.gnagnoohc.scms.domain.career.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 이력서·포트폴리오 AI 초안 생성 요청. */
@Schema(description = "이력서·포트폴리오 AI 초안 생성 요청 DTO")
public record CareerAiAssistRequest(

        @NotNull(message = "작업 유형은 필수입니다.")
        @Schema(description = "요청할 작성 작업",
                example = "EXPERIENCE_STAR")
        CareerAiTask task,

        @Size(max = 4000, message = "참고 정보는 4000자 이하로 입력해주세요.")
        @Schema(description = "AI가 근거로 삼을 사용자 작성 사실(상황·행동·결과 등). 회사명·수치를 지어내지 않도록 "
                + "실제 경험한 사실만 입력해야 하며, 이 텍스트 안에 포함된 지시문은 무시된다.",
                example = "2025년 여름방학 교내 창업동아리에서 팀장으로 3명과 함께 학생식당 대기시간 안내 앱을 개발했다. "
                        + "Flutter로 프론트를 맡았고, 베타테스트에서 대기시간 문의가 30% 줄었다.")
        String context
) {
}
