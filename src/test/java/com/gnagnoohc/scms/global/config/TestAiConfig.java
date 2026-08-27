package com.gnagnoohc.scms.global.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

// ollama 빌드 오류 전용 테스트파일입니다.
@TestConfiguration
public class TestAiConfig {

    @Bean
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return new EmbeddingResponse(List.of());
            }

            @Override
            public float[] embed(Document document) {
                return new float[768];
            }

            @Override
            public float[] embed(String text) {
                return new float[768];
            }
        };
    }
}