package com.gnagnoohc.scms.domain.counsel.dto.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 학생의 상담 전 AI 도우미 대화 요청.
 *
 * <p>서버는 대화 상태를 저장하지 않으므로, FE가 직전 응답의 초안·후보·조건을 context로 되돌려 보낸다.
 * context는 클라이언트 입력이므로 신뢰하지 않는다. 실제 신청 가능 여부는 서비스가 다시 대조하고,
 * 최종 판정은 기존 예약 POST가 한다.</p>
 */
public record CounselingAiChatRequest(
        @NotBlank @Size(max = 3000) String message,
        @Valid Context context
) {
    // @Size보다 trim이 먼저 일어나야 공백 포함 3,000자 초과와 정확히 3,000자를 올바르게 구분한다.
    // 초과분을 조용히 잘라 처리하지 않고 검증 실패(C001)로 돌려보낸다.
    public CounselingAiChatRequest {
        message = message == null ? null : message.trim();
    }

    /** 직전 응답에서 FE가 보관하던 대화 상태. */
    public record Context(
            @NotNull @PositiveOrZero Integer version,
            @Valid Draft draft,
            @Size(max = 5) List<@Valid @NotNull Candidate> candidates,
            boolean awaitingConfirmation,
            @Valid Filters filters
    ) {
    }

    /** 신청 초안. 일정을 골랐다면 그 일정의 유형도 함께 있어야 한다. */
    public record Draft(
            @Positive Integer counselingTypeId,
            @Positive Integer scheduleId,
            @Size(max = 3000) String requestContent
    ) {
        // 빈 문자열과 null을 모두 "내용 없음"(null)으로 맞춘다. 둘을 다르게 보면 내용이 그대로인데도
        // 초안이 바뀐 것으로 판정돼 정상적인 신청 확인("응")이 막힐 수 있다.
        public Draft {
            requestContent = requestContent == null || requestContent.isBlank() ? null : requestContent.trim();
        }

        // 유형 없이 일정만 있는 초안은 어느 유형의 일정인지 알 수 없어 가용성 대조가 불가능하다.
        // 응답 JSON에 필드로 노출되지 않도록 @JsonIgnore를 둔다.
        @JsonIgnore
        @AssertTrue
        public boolean isTypePresentWhenScheduleSelected() {
            return scheduleId == null || counselingTypeId != null;
        }
    }

    /** 직전에 화면에 표시된 후보. 모델은 이 목록의 인덱스만 고를 수 있다. */
    public record Candidate(
            @NotNull @Positive Integer counselingTypeId,
            @NotNull @Positive Integer scheduleId
    ) {
    }

    /** 일정 조회 조건. 각 필드 null은 제한 없음이며 양끝을 포함한다. 응답에서도 같은 형식으로 재사용한다. */
    public record Filters(
            LocalDate dateFrom,
            LocalDate dateTo,
            @JsonFormat(pattern = "HH:mm") LocalTime startTimeFrom,
            @JsonFormat(pattern = "HH:mm") LocalTime startTimeTo
    ) {
        // 기간이 뒤집히면 항상 0건이 되므로 입력 단계에서 거절한다.
        @JsonIgnore
        @AssertTrue
        public boolean isDateRangeValid() {
            return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
        }

        @JsonIgnore
        @AssertTrue
        public boolean isTimeRangeValid() {
            return startTimeFrom == null || startTimeTo == null || !startTimeFrom.isAfter(startTimeTo);
        }
    }
}
