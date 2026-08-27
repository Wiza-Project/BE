package com.gnagnoohc.scms.global.common.ncs;

import com.gnagnoohc.scms.domain.career.service.NcsEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

//TODO: 배포 상황에서 절대 사용하지 마세요. Ollama 사용 시, 무조건 로컬 설정 후 사용하세요. 배포 상황에서 사용 시 비용 청구합니다.

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("test")
public class NcsEmbeddingSyncRunner implements ApplicationRunner {

    private final NcsEmbeddingService ncsEmbeddingService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[NCS] 로컬 임베딩 동기화 러너 시작...");
        try {
            ncsEmbeddingService.generateAndSaveEmbeddings();
            log.info("[NCS] 로컬 임베딩 동기화 러너 완료");
        } catch (Exception e) {
            log.error("[NCS] 로컬 임베딩 동기화 러너 실패", e);
        }
    }
}