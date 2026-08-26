package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
import com.gnagnoohc.scms.domain.career.repository.NcsStandardRepository;
import com.gnagnoohc.scms.global.common.ncs.NcsApiClient;
import com.gnagnoohc.scms.global.common.ncs.NcsSubItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NcsStandardService {

    private final NcsApiClient ncsApiClient;
    private final NcsStandardRepository ncsStandardRepository;

    /**
     * NCS 세분류 목록을 조회하여 ncs_standard 테이블에 적재합니다.
     */
    @Transactional
    public int syncNcsStandard() {
        if (ncsStandardRepository.count() > 0) {
            log.info("[NCS] ncs_standard 원장에 이미 데이터가 존재하여 적재를 건너뜁니다.");
            return 0;
        }

        List<NcsSubItem> subItems = ncsApiClient.fetchAllSubcategories();
        if (subItems == null || subItems.isEmpty()) {
            log.warn("[NCS] 세분류 API 응답이 비어 있습니다.");
            return 0;
        }

        // 8자리 ncsCode 기준으로 중복 엔티티 사전 제거 (유니크 충돌 방지)
        Map<String, NcsStandard> uniqueMap = new LinkedHashMap<>();

        for (NcsSubItem item : subItems) {
            if (item.subCategoryCode() == null || item.subCategoryCode().isBlank()) {
                continue;
            }

            // 8자리 고유 코드 조립 (대2 + 중2 + 소2 + 세2)
            String fullNcsCode = item.largeCategoryCode()
                    + item.mediumCategoryCode()
                    + item.smallCategoryCode()
                    + item.subCategoryCode();

            // 중복된 코드는 최초 1건만 등록하고 스킵
            if (uniqueMap.containsKey(fullNcsCode)) {
                continue;
            }

            String desc = (item.jobDescription() != null && !item.jobDescription().isBlank())
                    ? item.jobDescription()
                    : item.subCategoryName();

            String combinedText = String.format("[%s > %s > %s] %s: %s",
                    item.largeCategoryName(),
                    item.mediumCategoryName(),
                    item.smallCategoryName(),
                    item.subCategoryName(),
                    desc);

            String categoryHierarchy = String.format("%s > %s > %s > %s",
                    item.largeCategoryName(),
                    item.mediumCategoryName(),
                    item.smallCategoryName(),
                    item.subCategoryName());

            NcsStandard entity = NcsStandard.of(
                    fullNcsCode,
                    categoryHierarchy,
                    combinedText
            );

            uniqueMap.put(fullNcsCode, entity);
        }

        List<NcsStandard> saveTargets = new ArrayList<>(uniqueMap.values());
        ncsStandardRepository.saveAll(saveTargets);
        log.info("[NCS] ncs_standard 직무 원장 {}건 최종 적재 완료", saveTargets.size());
        return saveTargets.size();
    }
}