package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
import com.gnagnoohc.scms.domain.career.repository.NcsStandardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

//TODO: 배포 상황에서 절대 사용하지 마세요. Ollama 사용 시, 무조건 로컬 설정 후 사용하세요. 배포 상황에서 사용 시 비용 청구합니다.

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("test")
public class NcsEmbeddingService {

    private final NcsStandardRepository ncsStandardRepository;
    private final EmbeddingModel embeddingModel;

    @Transactional
    public void generateAndSaveEmbeddings() {
        List<NcsStandard> ncsList = ncsStandardRepository.findAll();
        log.info("[NCS] 전체 직무 데이터 건수: {}건", ncsList.size());

        int targetCount = 0;
        int successCount = 0;

        for (NcsStandard ncs : ncsList) {
            // 이미 임베딩 벡터가 존재하는 항목은 중복 연산 건너뜀
            if (ncs.getEmbeddingVector() != null) {
                continue;
            }

            targetCount++;
            String textToEmbed = String.format("[%s] %s", ncs.getCategoryName(), ncs.getJobDescription());

            try {
                float[] vector = embeddingModel.embed(textToEmbed);
                ncs.updateEmbeddingVector(vector);
                successCount++;

                if (successCount % 50 == 0) {
                    log.info("[NCS] 벡터 임베딩 진행 중... ({}건 완료)", successCount);
                }
            } catch (Exception e) {
                log.error("[NCS] 임베딩 생성 실패 - 코드: {}, 명칭: {}", ncs.getNcsCode(), ncs.getCategoryName(), e);
            }
        }

        log.info("[NCS] 벡터 임베딩 동기화 완료 - 대상: {}건 중 성공: {}건", targetCount, successCount);
    }
}