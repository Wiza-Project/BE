package com.gnagnoohc.scms.domain.counsel.dto.ai;

import java.time.Instant;
import java.util.List;

/** 상담 전 AI 안내, 신청 초안, 실제 예약 가능 일정 응답. */
public record CounselingAiChatResponse(
        String reply,
        int basedOnVersion,
        Action action,
        CounselingAiChatRequest.Draft draft,
        CounselingAiChatRequest.Filters filters,
        CounselingTypeRecommendation counselingTypeRecommendation,
        List<ScheduleRecommendation> scheduleRecommendations
) {

    /** FE가 다음 동작을 고르는 신호다. DB enum이 아니다. */
    public enum Action {
        INFORM,
        UPDATE_DRAFT,
        ASK_CONFIRMATION,
        CONFIRM_RESERVATION,
        OPEN_GENERAL_APPLICATION
    }

    /** 의료적 진단이 아니라 대화 내용을 바탕으로 한 상담 유형 안내다. */
    public record CounselingTypeRecommendation(
            Integer counselingTypeId,
            String typeCode,
            String typeName,
            String reason
    ) {
    }

    /** 상담 유형 API와 일정 API에서 이미 검증된 예약 가능 일정만 담는다. */
    public record ScheduleRecommendation(
            Integer scheduleId,
            Integer counselingTypeId,
            String counselingTypeCode,
            String counselingTypeName,
            String counselorName,
            String counselorDepartmentName,
            Instant startsAt,
            Instant endsAt,
            Instant bookingDeadline,
            String location,
            Integer remainingCapacity,
            String recommendationReason
    ) {
    }
}
