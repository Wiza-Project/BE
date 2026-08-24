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
 * <p>{@code sort_order}는 실행 시점의 조회범위(firstYear)가 아니라
 * {@link #SORT_ORDER_EPOCH_YEAR}라는 고정 기준점에서 계산한다({@code year - EPOCH_YEAR}) —
 * firstYear는 매년 앞으로 밀리는데(연도가 바뀔 때마다 range가 이동) 거기에 상대적으로
 * sort_order를 매기면, 같은 연도라도 "언제 처음 삽입됐는지"에 따라 값이 달라져서
 * {@code ORDER BY sort_order}가 해를 거듭할수록 뒤죽박죽이 된다. 고정 기준점을 쓰면
 * 어느 해에 삽입되든 같은 연도는 항상 같은 sort_order를 받는다. 이미 옛 방식(firstYear
 * 상대값)으로 들어간 행이 있을 수 있어, 매 기동 시 전체 {@code ACADEMIC_YEAR} 행의
 * sort_order를 이 규칙대로 재계산해 정합화한다({@link #run} 마지막 UPDATE).</p>
 *
 * <p>{@code docs/ddl/2026-08-20_common_code_seed.sql}의 정적 스냅샷도 같은 규칙
 * (2024학년도=1, {@code SORT_ORDER_EPOCH_YEAR}=2023)로 값을 매겨뒀다 — 상수를 바꾸면
 * 그 파일도 같이 맞춰야 한다. 2023이라는 값 자체엔 의미가 없다({@link #SORT_ORDER_EPOCH_YEAR}
 * 참고 — 개교년도 같은 게 아니라 임의로 고른 고정 기준일 뿐).</p>
 */
@Slf4j
@Component
@Profile({"local", "prod"})
@RequiredArgsConstructor
public class AcademicYearCodeExtender implements CommandLineRunner {

    private static final int PAST_WINDOW = 4;
    private static final int FUTURE_WINDOW = 3;

    /**
     * sort_order 계산의 고정 기준점.
     */
    private static final int SORT_ORDER_EPOCH_YEAR = 2023;

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
            int sortOrder = year - SORT_ORDER_EPOCH_YEAR;
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

        // 예전(firstYear 상대값) 방식으로 이미 들어간 행이 있으면 고정 기준점 규칙으로
        // 재계산해 정합화한다. 매번 같은 값으로 수렴하는 멱등 연산이라 안전하다.
        int reconciled = jdbcTemplate.update("""
                UPDATE common_code
                SET sort_order = (code::int - ?)
                WHERE code_group = 'ACADEMIC_YEAR' AND sort_order <> (code::int - ?)
                """,
                SORT_ORDER_EPOCH_YEAR, SORT_ORDER_EPOCH_YEAR);
        if (reconciled > 0) {
            log.info("ACADEMIC_YEAR sort_order {}건 정합화", reconciled);
        }
    }
}
