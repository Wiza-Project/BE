package com.gnagnoohc.scms.domain.career.dto.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    @Test
    void careerWithEndDateBeforeStartDate_isInvalid() {
        ResumeCareerDTO career = new ResumeCareerDTO(
                "테스트 회사", "개발자", LocalDate.of(2025, 2, 1), LocalDate.of(2025, 1, 31), null);

        assertThat(validator.validate(career))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("dateRangeValid");
    }

    @Test
    void careerDateRangeValidationMethod_isNotSerialized() {
        ResumeCareerDTO career = new ResumeCareerDTO(
                "테스트 회사", "개발자", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), null);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        assertThat(objectMapper.valueToTree(career).has("dateRangeValid")).isFalse();
    }

    @Test
    void createRequestWithTitleOver200Characters_isInvalid() {
        ResumeCreateRequestDTO request = new ResumeCreateRequestDTO();
        ReflectionTestUtils.setField(request, "documentTitle", "a".repeat(201));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("documentTitle");
    }

    @Test
    void updateRequestWithTitleOver200Characters_isInvalid() {
        ResumeUpdateRequestDTO request = new ResumeUpdateRequestDTO();
        ReflectionTestUtils.setField(request, "documentTitle", "a".repeat(201));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("documentTitle");
    }
}
