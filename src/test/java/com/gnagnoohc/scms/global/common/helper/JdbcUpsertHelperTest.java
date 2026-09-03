package com.gnagnoohc.scms.global.common.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcUpsertHelper 단위 테스트")
class JdbcUpsertHelperTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private JdbcUpsertHelper jdbcUpsertHelper;

    @Nested
    @DisplayName("executeInsertDoNothing 메서드는")
    class Describe_executeInsertDoNothing {

        @Test
        @DisplayName("SQL과 파라미터를 받아 JdbcTemplate.update를 호출하고 영향받은 행 수를 반환한다")
        void it_executes_update_and_returns_row_count() {
            String sql = "INSERT INTO test_table (id, val) VALUES (?, ?) ON CONFLICT DO NOTHING";
            given(jdbcTemplate.update(sql, 1L, "TEST")).willReturn(1);

            int result = jdbcUpsertHelper.executeInsertDoNothing(sql, 1L, "TEST");

            assertThat(result).isEqualTo(1);
            verify(jdbcTemplate).update(sql, 1L, "TEST");
        }
    }

    @Nested
    @DisplayName("exists 메서드는")
    class Describe_exists {

        @Test
        @DisplayName("조회된 카운트가 1 이상이면 true를 반환한다")
        void it_returns_true_when_count_is_greater_than_zero() {
            String sql = "SELECT COUNT(*) FROM test_table WHERE id = ?";
            given(jdbcTemplate.queryForObject(sql, Integer.class, 1L)).willReturn(1);

            boolean result = jdbcUpsertHelper.exists(sql, 1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("조회된 카운트가 0이거나 null이면 false를 반환한다")
        void it_returns_false_when_count_is_zero_or_null() {
            String sql = "SELECT COUNT(*) FROM test_table WHERE id = ?";
            given(jdbcTemplate.queryForObject(sql, Integer.class, 999L)).willReturn(0);

            boolean result = jdbcUpsertHelper.exists(sql, 999L);

            assertThat(result).isFalse();
        }
    }
}