package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * career-ai 호출을 학생 단위로 제한한다.
 */
@Component
public class CareerAiRateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final int MAX_REQUESTS_PER_DAY = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ConcurrentHashMap<Integer, AtomicBoolean> inFlightByStudent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, WindowCounter> minuteWindowByStudent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, WindowCounter> dayWindowByStudent = new ConcurrentHashMap<>();

    /** 동시 요청 1건·분당/일별 한도를 확인한 뒤 action을 실행한다. 실행 여부와 무관하게 동시 요청 슬롯은 반드시 반납한다. */
    public <T> T withLimit(Integer studentUserId, Supplier<T> action) {
        acquireConcurrencySlot(studentUserId);
        try {
            consumeWindow(minuteWindowByStudent, studentUserId, currentMinuteKey(), MAX_REQUESTS_PER_MINUTE);
            consumeWindow(dayWindowByStudent, studentUserId, currentDayKey(), MAX_REQUESTS_PER_DAY);
            return action.get();
        } finally {
            releaseConcurrencySlot(studentUserId);
        }
    }

    private void acquireConcurrencySlot(Integer studentUserId) {
        AtomicBoolean slot = inFlightByStudent.computeIfAbsent(studentUserId, id -> new AtomicBoolean(false));
        if (!slot.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.CAREER_AI_CONCURRENT_REQUEST);
        }
    }

    private void releaseConcurrencySlot(Integer studentUserId) {
        AtomicBoolean slot = inFlightByStudent.get(studentUserId);
        if (slot != null) {
            slot.set(false);
        }
    }

    private void consumeWindow(ConcurrentHashMap<Integer, WindowCounter> windows, Integer studentUserId,
                                long windowKey, int limit) {
        WindowCounter counter = windows.computeIfAbsent(studentUserId, id -> new WindowCounter());
        synchronized (counter) {
            if (counter.windowKey != windowKey) {
                counter.windowKey = windowKey;
                counter.count = 0;
            }
            counter.count++;
            if (counter.count > limit) {
                throw new BusinessException(ErrorCode.CAREER_AI_RATE_LIMIT_EXCEEDED);
            }
        }
    }

    private long currentMinuteKey() {
        return Instant.now().getEpochSecond() / 60;
    }

    private long currentDayKey() {
        return LocalDate.now(KST).toEpochDay();
    }

    private static final class WindowCounter {
        private long windowKey = Long.MIN_VALUE;
        private int count;
    }
}
