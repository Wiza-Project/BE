package com.gnagnoohc.scms.domain.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WP-106: 아이디(학번·교번, universityNo)는 공백 없이 숫자 8자리로만 구성되어야 합니다.
 * 안내 메시지는 학번/교번을 구분하지 않고 "아이디"로 통일합니다
 * (프론트에서 "아이디는 학번 또는 교번입니다" 안내를 별도로 노출).
 * 앞뒤 공백을 트리밍해서 통과시키지 않고, 그대로 형식 오류로 거부합니다.
 */
class LoginRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void universityNo_withExactlyEightDigits_passesValidation() {
        LoginRequest request = new LoginRequest("20210001", "password1234");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2021abcd",       // 숫자 외 문자 포함
            "2021-001",       // 하이픈 포함
            " 2021001",       // 앞 공백
            "2021001 ",       // 뒤 공백
            "2021 001",       // 중간 공백
            "학번12345",       // 한글 포함
            "2021001",        // 7자리 (부족)
            "202100012",      // 9자리 (초과)
    })
    void universityNo_notExactlyEightDigits_failsValidation(String universityNo) {
        LoginRequest request = new LoginRequest(universityNo, "password1234");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("아이디는 숫자 8자리로 입력해주세요.");
    }

    @Test
    void universityNo_blank_failsWithNotBlankOnly() {
        LoginRequest request = new LoginRequest("", "password1234");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        // 빈 문자열은 @Pattern(\\d{8}) 에도 걸리므로 두 메시지가 모두 나올 수 있습니다
        // (프론트는 NotBlank 메시지를 우선 노출).
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("아이디를 입력해주세요.");
    }

    @Test
    void password_within100Chars_passesValidation() {
        LoginRequest request = new LoginRequest("20210001", "a".repeat(100));

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void password_over100Chars_failsValidation() {
        LoginRequest request = new LoginRequest("20210001", "a".repeat(101));

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("비밀번호가 너무 깁니다.");
    }
}
