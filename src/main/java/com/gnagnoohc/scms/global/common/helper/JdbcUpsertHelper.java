package com.gnagnoohc.scms.global.common.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;

/**
 * PostgreSQL ON CONFLICT 기반의 안전한 멱등성 INSERT / UPDATE(업서트) 실행 헬퍼.
 */
@Component
@RequiredArgsConstructor
public class JdbcUpsertHelper {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 유니크 충돌 시 무시(DO NOTHING)하는 멱등성 INSERT를 수행합니다.
     *
     * @return 영향받은 행 수 (0이면 이미 존재하여 스킵됨, 1이면 신규 삽입됨)
     */
    public int executeInsertDoNothing(String sql, Object... params) {
        return jdbcTemplate.update(sql, normalizeParameters(params));
    }

    /**
     * 대상 테이블에 특정 조건의 레코드가 이미 존재하는지 여부를 빠르게 확인합니다.
     */
    public boolean exists(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, normalizeParameters(params));
        return count != null && count > 0;
    }

    /**
     * PostgreSQL JDBC 드라이버의 java.time.Instant 타입 바인딩 추론 실패 방지 및 파라미터 정규화
     *
     * <p>Native SQL 가변인자로 전달된 {@link Instant} 객체를 JDBC 표준 {@link Timestamp}로 변환하여,
     * PostgreSQL timestamptz 컬럼 바인딩 시 발생하는 타입 추론 실패 예외(PSQLException)를 전역에서 안전하게 차단합니다.</p>
     *
     * @param params SQL 실행에 전달될 원본 가변인자 파라미터 배열
     * @return Instant 타입이 Timestamp로 정규화된 안전한 파라미터 배열
     */
    private Object[] normalizeParameters(Object[] params) {
        if (params == null) {
            return new Object[0];
        }
        return Arrays.stream(params)
                .map(p -> (p instanceof Instant instant) ? Timestamp.from(instant) : p)
                .toArray();
    }

}