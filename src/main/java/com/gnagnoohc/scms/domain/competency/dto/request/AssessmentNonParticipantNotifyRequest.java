package com.gnagnoohc.scms.domain.competency.dto.request;

import java.util.List;

/**
 * 미응시자 알림 발송 요청. userIds가 null(또는 생략)이면 회차 전체 미응시자를 대상으로 한다.
 * 값이 오면 서비스가 그 목록을 그대로 신뢰하지 않고, 실제 미응시자 집합과의 교집합만 발송
 * 대상으로 삼는다(AssessmentNonParticipantService.notify 참고).
 */
public record AssessmentNonParticipantNotifyRequest(
        List<Integer> userIds
) {}
