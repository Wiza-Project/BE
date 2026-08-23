package com.gnagnoohc.scms.global.error;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * DB 무결성 제약조건 위반 예외(DataIntegrityViolationException)의
 * 원인 메시지 및 제약조건/컬럼 토큰 포함 여부를 안전하게 검사하는 공통 판별기.
 */
public final class DbConstraintViolationMatcher {

    private DbConstraintViolationMatcher() {
    }

    public static boolean contains(DataIntegrityViolationException exception, String token) {
        if (exception == null || token == null || token.isBlank()) {
            return false;
        }
        Throwable rootCause = exception.getMostSpecificCause();
        String message = rootCause.getMessage();
        return message != null && message.contains(token);
    }
}