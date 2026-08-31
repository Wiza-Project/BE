package com.gnagnoohc.scms.global.common.notification;

/**
 * 알림 유형 코드. 요구사항 문서의 "알림 유형코드"에 대응한다.
 * 새 값을 추가하면 팀 채널에 반드시 공유할 것 — 다른 도메인이 이 값을 그대로 참조한다.
 */
public enum NotificationType {
    APPROVED,                    // 승인
    REJECTED,                    // 반려
    REVISION_REQUESTED,          // 수정요청
    DEADLINE_IMMINENT,           // 마감임박
    RESERVATION_CONFIRMED,       // 상담확정
    MILEAGE_EARNED,              // 마일리지 적립완료
    EVIDENCE_SUPPLEMENT_NEEDED,  // 증빙보완 필요
    WAITLIST_SLOT_OPENED,        // 대기 자리 발생
    APPLICATION_SUBMITTED,       // 지원/신청 접수완료 (채용공고/프로그램 등)
}
