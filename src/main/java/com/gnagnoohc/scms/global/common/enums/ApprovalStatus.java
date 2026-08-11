package com.gnagnoohc.scms.global.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로세스 모델에서 "승인" 흐름이 최소 4곳 반복됩니다.
 *  - P1100 분류체계 요청 → 학생역량센터 승인
 *  - P1100 비교과프로그램 신청 → 학생역량센터 승인
 *  - P1200 프로그램 참여신청 → 비교과운영부서 참여승인
 *  - P4100 마일리지 실적신청 → 학생역량센터 신청승인
 *
 * 도메인마다 상태 enum 을 따로 만들지 말고 이 하나를 재사용하세요.
 * 반려 사유는 각 엔티티의 rejectReason 필드로 관리합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ApprovalStatus {

    REQUESTED("신청"),
    APPROVED("승인"),
    REJECTED("반려"),
    CANCELED("취소");

    private final String label;

    public boolean isFinal() {
        return this != REQUESTED;
    }
}
