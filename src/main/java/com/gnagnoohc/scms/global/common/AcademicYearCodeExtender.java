package com.gnagnoohc.scms.global.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;

/**
 * {@code common_code}의 {@code ACADEMIC_YEAR} 그룹을 "현재연도 - {@link #PAST_WINDOW} ~
 * 현재연도 + {@link #FUTURE_WINDOW}" 범위로 매 앱 기동마다 자동으로 채운다.
 *
 * <p>{@link CommonCodeSeeder}(local 전용, 개발 편의용)와 달리 이 컴포넌트는 <b>local과
 * 운영(prod) 양쪽에서 다 실행된다</b>.</p>
 *
 */
@Slf4j
@Component
@Profile({"local", "prod"})
@RequiredArgsConstructor
public class AcademicYearCodeExtender implements CommandLineRunner {

    private static final int PAST_WINDOW = 4;
    private static final int FUTURE_WINDOW = 3;

    /** 시드 데이터의 created_by 값. 실사용자가 아니라 시스템 시드임을 나타내는 관례상의 값. */
    private static final int SYSTEM_CREATED_BY = 0;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        int currentYear = Year.now().getValue();
        int firstYear = currentYear - PAST_WINDOW;
        int lastYear = currentYear + FUTURE_WINDOW;
        Instant now = Instant.now();

        int inserted = 0;
        for (int year = firstYear; year <= lastYear; year++) {
            int sortOrder = year - firstYear + 1;
            inserted += jdbcTemplate.update("""
                    INSERT INTO common_code
                        (code_group, code, code_name, sort_order, is_active, created_by, created_at, updated_at)
                    VALUES ('ACADEMIC_YEAR', ?, ?, ?, true, ?, ?, ?)
                    ON CONFLICT (code_group, code) DO NOTHING
                    """,
                    String.valueOf(year), year + "학년도", sortOrder,
                    SYSTEM_CREATED_BY, Timestamp.from(now), Timestamp.from(now));
        }
        if (inserted > 0) {
            log.info("ACADEMIC_YEAR 공통코드 {}건 추가 시딩 (범위 {}~{})", inserted, firstYear, lastYear);
        }
    }
}
