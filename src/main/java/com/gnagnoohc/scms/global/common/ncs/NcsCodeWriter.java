package com.gnagnoohc.scms.global.common.ncs;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * NCS_CODE 공통코드 일괄 삽입. {@link NcsCodeSyncRunner}가 호출하는 별도 빈으로 분리해서
 * {@code @Transactional}이 (같은 클래스 내부 self-invocation 없이) 정상적으로 걸리게 한다 —
 * 도중에 실패하면 전부 롤백되어, "그룹에 행이 하나라도 있으면 완전히 적재된 것"이라는
 * 러너 쪽 스킵 판단의 전제가 깨지지 않는다.
 */
@Component
@RequiredArgsConstructor
class NcsCodeWriter {

    static final String CODE_GROUP = "NCS_CODE";
    private static final String CODE_PREFIX = "NC";
    /** 시드 데이터의 created_by 값과 동일한 관례 — 실사용자가 아닌 시스템 동기화임을 나타냄. */
    private static final int SYSTEM_CREATED_BY = 0;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    int insertAll(List<NcsLclasItem> items) {
        List<NcsLclasItem> sorted = items.stream()
                .sorted(Comparator.comparing(NcsLclasItem::code))
                .toList();

        Instant now = Instant.now();
        int sortOrder = 0;
        int inserted = 0;
        for (NcsLclasItem item : sorted) {
            sortOrder++;
            // 접두어(NC)+100단위 일련번호 — PROGRAM_TYPE(PT100...), DEPARTMENT(D100...)와 동일 관례.
            String code = CODE_PREFIX + (sortOrder * 100);
            inserted += jdbcTemplate.update("""
                    INSERT INTO common_code
                        (code_group, code, code_name, description, sort_order, is_active, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, true, ?, ?, ?)
                    ON CONFLICT (code_group, code) DO NOTHING
                    """,
                    CODE_GROUP, code, item.name(), item.code(), sortOrder,
                    SYSTEM_CREATED_BY, Timestamp.from(now), Timestamp.from(now));
        }
        return inserted;
    }
}
