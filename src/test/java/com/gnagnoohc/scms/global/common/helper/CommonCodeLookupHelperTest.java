package com.gnagnoohc.scms.global.common.helper;

import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommonCodeLookupHelper 단위 테스트")
class CommonCodeLookupHelperTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CommonCodeLookupHelper commonCodeLookupHelper;

    @Nested
    @DisplayName("validateActiveCode 메서드는")
    class Describe_validateActiveCode {

        @Test
        @DisplayName("코드가 null이거나 비어있으면 쿼리를 실행하지 않고 통과한다")
        void it_passes_when_code_is_blank() {
            commonCodeLookupHelper.validateActiveCode("REGION", null);
            commonCodeLookupHelper.validateActiveCode("REGION", "   ");

            verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(Integer.class), any(), any());
        }

        @Test
        @DisplayName("활성화된 코드가 존재하면 정상 통과한다")
        void it_passes_when_active_code_exists() {
            given(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq("REGION"), eq("SEOUL")))
                    .willReturn(1);

            commonCodeLookupHelper.validateActiveCode("REGION", "SEOUL");

            verify(jdbcTemplate).queryForObject(any(String.class), eq(Integer.class), eq("REGION"), eq("SEOUL"));
        }

        @Test
        @DisplayName("존재하지 않거나 비활성화된 코드이면 INVALID_INPUT BusinessException을 발생시킨다")
        void it_throws_exception_when_code_not_found() {
            given(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq("REGION"), eq("INVALID")))
                    .willReturn(0);

            assertThatThrownBy(() -> commonCodeLookupHelper.validateActiveCode("REGION", "INVALID"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("getCodeName 메서드는")
    class Describe_getCodeName {

        @Test
        @DisplayName("코드가 유효하면 조회된 코드명을 반환한다")
        void it_returns_code_name() {
            given(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq("REGION"), eq("SEOUL")))
                    .willReturn("서울특별시");

            String codeName = commonCodeLookupHelper.getCodeName("REGION", "SEOUL");

            assertThat(codeName).isEqualTo("서울특별시");
        }

        @Test
        @DisplayName("코드가 null이면 null을 반환한다")
        void it_returns_null_when_code_is_null() {
            String codeName = commonCodeLookupHelper.getCodeName("REGION", null);
            assertThat(codeName).isNull();
        }
    }
}