package com.gnagnoohc.scms.global.common.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
        return jdbcTemplate.update(sql, params);
    }

    /**
     * 대상 테이블에 특정 조건의 레코드가 이미 존재하는지 여부를 빠르게 확인합니다.
     */
    public boolean exists(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }
}