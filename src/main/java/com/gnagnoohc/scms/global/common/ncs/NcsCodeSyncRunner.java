package com.gnagnoohc.scms.global.common.ncs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NCS_CODE 공통코드 최초 적재 러너 .
 *
 * <p>국가직무능력표준(NCS) 대분류 24종을 공공데이터포털 API(NCS001, {@link NcsApiClient})에서
 * 받아와 common_code(code_group=NCS_CODE)에 <b>최초 1회만</b> 적재한다. code_group=NCS_CODE로
 * 이미 행이 하나라도 있으면 API를 호출하지 않고 즉시 스킵한다 — 이후 값 수정/추가는 관리자가
 * common_code를 직접 관리(수동)하며, 이 러너가 자동으로 갱신하는 일은 없다.</p>
 *
 * <p><b>다른 그룹과의 차이:</b> {@link com.gnagnoohc.scms.global.common.CommonCodeSeeder}의
 * SEEDS는 정적 목록이라 로컬 전용(local 프로필)이지만, NCS_CODE는 실제 서버 DB에도 최초 1회
 * 적재가 필요해 정적 목록으로 두지 않았다. 그래서 CommonCodeSeeder.SEEDS에는 넣지 않고 이
 * 별도 러너로 분리했으며, 프로필도 local 한정이 아니라 테스트만 제외한다(실제 네트워크 호출이
 * 테스트에서 발생하면 CI가 외부 API 가용성에 종속되어 불안정해지기 때문).</p>
 *
 * <p>{@code NcsStandard}(임베딩 기반 세분류/매칭 전용, {@code JobPreference}가 참조)와는
 * 무관하다 — 이 러너는 {@code JobPosting.ncsCode}가 참조하는 common_code 쪽 대분류
 * 드롭다운 값만 채운다.</p>
 *
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NcsCodeSyncRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final NcsApiClient ncsApiClient;
    private final NcsCodeWriter ncsCodeWriter;

    @Override
    public void run(String... args) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM common_code WHERE code_group = ?", Integer.class, NcsCodeWriter.CODE_GROUP);
        if (existing != null && existing > 0) {
            log.debug("NCS_CODE 이미 적재됨({}건) — API 호출 없이 스킵", existing);
            return;
        }

        try {
            List<NcsLclasItem> items = ncsApiClient.fetchAllLargeCategories();
            if (items.isEmpty()) {
                log.warn("NCS 대분류 API 응답이 비어있어 NCS_CODE를 적재하지 않습니다.");
                return;
            }
            int inserted = ncsCodeWriter.insertAll(items);
            log.info("NCS_CODE 최초 적재 완료 — {}건 삽입 (국가 NCS 대분류 API)", inserted);
        } catch (Exception e) {
            log.error("NCS_CODE 최초 적재 실패 — 이번 기동에서는 건너뛰고 다음 기동에서 재시도합니다.", e);
        }
    }
}
