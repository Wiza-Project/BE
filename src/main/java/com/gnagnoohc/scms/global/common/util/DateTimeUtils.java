package com.gnagnoohc.scms.global.common.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 전역 일시/시간대 변환 유틸리티 클래스
 */
public final class DateTimeUtils {

    public static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private DateTimeUtils() {
    }

    /**
     * UTC Instant 타임스탬프를 한국 표준시(Asia/Seoul, KST) 기준의 OffsetDateTime으로 안전하게 변환하는 연산 로직
     *
     * @param instant UTC 타임스탬프 인스턴스 (nullable)
     * @return 한국 표준시 오프셋이 적용된 OffsetDateTime (instant가 null이면 null 반환)
     */
    public static OffsetDateTime toKstOffsetDateTime(Instant instant) {
        return (instant == null) ? null : instant.atZone(KST_ZONE).toOffsetDateTime();
    }
}