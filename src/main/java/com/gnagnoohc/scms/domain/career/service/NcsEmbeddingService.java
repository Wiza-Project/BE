package com.gnagnoohc.scms.domain.career.service;

/*
import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
import com.gnagnoohc.scms.domain.career.repository.NcsStandardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
*/

/*
 // TODO: 배포 상황에서 절대 사용하지 마세요. Ollama 사용 시, 무조건 로컬 설정 후 사용하세요. 배포 상황에서 사용 시 비용 청구합니다.
 // 주의: 배포(prod) 및 CI/테스트(test) 환경에서는 절대 실행하거나 활성화하지 마세요.
 //
 // 평상시에는 matchIfMissing = false 및 havingValue = "true" 제약으로 인해
 // 스프링 컨텍스트 로딩 시 완벽히 비활성화됩니다.
 //
 // [로컬 임베딩 재추출 및 활성화 순서]
 // 1. build.gradle: 'spring-ai-starter-model-ollama' 의존성 주석 해제 후 Gradle Sync
 // 2. NcsEmbeddingService.java & NcsEmbeddingSyncRunner.java: 내부 로직 주석 해제
 // 3. application-local.yml: 아래 설정 추가
 //    app:
 //      embedding:
 //        enabled: true
 //    spring:
 //      ai:
 //        model:
 //          embedding: ollama
 //        ollama:
 //          base-url: http://localhost:11434
 //          embedding:
 //            model: nomic-embed-text
 // 4. Ollama 기동: 로컬 터미널에서 'ollama run nomic-embed-text' 실행
 // 5. 실행: SPRING_PROFILES_ACTIVE=local 로 애플리케이션 1회 기동하여 DB 적재 완료
 // 6. 원복: 작업 완료 후 커밋/푸시 전 내부 로직 및 의존성 주석 처리 원복 (CI 빌드 방어)
 */

/*
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("local")
@ConditionalOnProperty(prefix = "app.embedding", name = "enabled", havingValue = "true")
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
*/