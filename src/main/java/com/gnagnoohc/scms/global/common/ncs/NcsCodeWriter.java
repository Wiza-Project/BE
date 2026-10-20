package com.gnagnoohc.scms.global.common.ncs;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private static final int EXPECTED_COUNT = 24;
    /** 시드 데이터의 created_by 값과 동일한 관례 — 실사용자가 아닌 시스템 동기화임을 나타냄. */
    private static final int SYSTEM_CREATED_BY = 0;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    int insertAll(List<NcsLclasItem> items) {
        validate(items);

        List<NcsLclasItem> sorted = items.stream()
                .sorted(Comparator.comparing(NcsLclasItem::code))
                .toList();

        Instant now = Instant.now();
        int inserted = 0;
        for (NcsLclasItem item : sorted) {
            // code는 응답 순서(위치)가 아니라 원본 NCS_LCLAS_CD의 숫자값으로 정한다 — API가
            // 부분 목록을 주더라도(검증에서 걸러지긴 하지만) 항상 올바른 대분류에 매핑되도록.
            int seq = Integer.parseInt(item.code());
            // 접두어(NC)+100단위 일련번호 — PROGRAM_TYPE(PT100...), DEPARTMENT(D100...)와 동일 관례.
            String code = CODE_PREFIX + (seq * 100);
            inserted += jdbcTemplate.update("""
                    INSERT INTO common_code
                        (code_group, code, code_name, description, sort_order, is_active, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, true, ?, ?, ?)
                    ON CONFLICT (code_group, code) DO NOTHING
                    """,
                    CODE_GROUP, code, item.name(), item.code(), seq,
                    SYSTEM_CREATED_BY, Timestamp.from(now), Timestamp.from(now));
        }
        return inserted;
    }

    /**
     * 삽입 전 원본 code 집합을 검증한다 — null/중복 없이 정확히 "01"~"24" 24종이어야 한다.
     * API가 부분 목록을 반환하는 경우(페이지네이션 오류, 응답 변경 등) 이 검증 없이는 잘못된
     * 매핑으로 조용히 저장되고, "행이 하나라도 있으면 완료"라는 스킵 전제 때문에 다음 기동에서도
     * 영영 고쳐지지 않는다. 검증 실패 시 예외를 던져 트랜잭션 전체를 롤백시킨다.
     */
    private void validate(List<NcsLclasItem> items) {
        if (items.stream().anyMatch(item -> item == null || item.code() == null || item.code().isBlank())) {
            throw new IllegalStateException("NCS 대분류 응답에 item 또는 code가 비어있는 항목이 있습니다: " + items);
        }
        if (items.stream().anyMatch(item -> item.name() == null || item.name().isBlank())) {
            throw new IllegalStateException("NCS 대분류 응답에 code_name이 비어있는 항목이 있습니다: " + items);
        }
        Set<Integer> actual = items.stream()
                .map(item -> Integer.parseInt(item.code()))
                .collect(Collectors.toSet());
        if (actual.size() != items.size()) {
            throw new IllegalStateException("NCS 대분류 응답에 중복된 code가 있습니다: " + items);
        }
        Set<Integer> expected = IntStream.rangeClosed(1, EXPECTED_COUNT).boxed().collect(Collectors.toSet());
        if (!actual.equals(expected)) {
            throw new IllegalStateException(
                    "NCS 대분류 응답이 예상 집합(01~" + EXPECTED_COUNT + ")과 다릅니다. 실제: " + actual);
        }
    }
}
