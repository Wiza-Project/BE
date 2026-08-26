package com.gnagnoohc.scms.domain.mileage.event;

import java.math.BigDecimal;

/** 외부활동 신청의 심사 결과를 알림 처리에 전달하는 마일리지 도메인 이벤트다. */
public record ExternalActivityClaimDecisionEvent(
        Integer externalClaimId,
        Integer studentId,
        String activityName,
        String claimStatus,
        BigDecimal transactionPoints,
        String reason
) {
}
