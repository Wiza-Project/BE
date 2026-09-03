package com.gnagnoohc.scms.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DbConstraintViolationMatcher 단위 테스트")
class DbConstraintViolationMatcherTest {

    private static final String TARGET_CONSTRAINT = "uq_student_job_preference";

    @Nested
    @DisplayName("contains 메서드는")
    class Describe_contains {

        @Test
        @DisplayName("예외의 원인 메시지에 제약조건명이 포함되어 있으면 true를 반환한다")
        void it_returns_true_when_token_matches() {
            // given
            Throwable rootCause = new RuntimeException("ERROR: duplicate key value violates unique constraint \"" + TARGET_CONSTRAINT + "\"");
            DataIntegrityViolationException exception = new DataIntegrityViolationException("Data integrity error", rootCause);

            // when
            boolean result = DbConstraintViolationMatcher.contains(exception, TARGET_CONSTRAINT);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("예외의 원인 메시지에 다른 제약조건명(예: NOT NULL)만 있으면 false를 반환한다")
        void it_returns_false_when_token_does_not_match() {
            // given
            Throwable rootCause = new RuntimeException("ERROR: null value in column \"created_by\" violates not-null constraint");
            DataIntegrityViolationException exception = new DataIntegrityViolationException("Data integrity error", rootCause);

            // when
            boolean result = DbConstraintViolationMatcher.contains(exception, TARGET_CONSTRAINT);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("예외 객체가 null이면 false를 반환한다")
        void it_returns_false_when_exception_is_null() {
            assertThat(DbConstraintViolationMatcher.contains(null, TARGET_CONSTRAINT)).isFalse();
        }

        @Test
        @DisplayName("검사할 토큰이 null이거나 공백이면 false를 반환한다")
        void it_returns_false_when_token_is_blank() {
            DataIntegrityViolationException exception = new DataIntegrityViolationException("Some DB Error");

            assertThat(DbConstraintViolationMatcher.contains(exception, null)).isFalse();
            assertThat(DbConstraintViolationMatcher.contains(exception, "")).isFalse();
            assertThat(DbConstraintViolationMatcher.contains(exception, "   ")).isFalse();
        }
    }
}