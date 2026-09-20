package com.gnagnoohc.scms.domain.career.helper;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 정보통신 / IT 개발 직군 키워드 동의어 매핑 사전
 * - 한/영 병행 표기, 약어, 외래어 표기 차이를 통합 관리
 */
@Component
public class TechKeywordDictionary {

    private static final Map<String, Set<String>> SYNONYM_MAP = Map.ofEntries(
            // 모바일 / 앱
            Map.entry("안드로이드", Set.of("android", "안드로이드")),
            Map.entry("android", Set.of("android", "안드로이드")),
            Map.entry("ios", Set.of("ios", "아이폰", "swift")),
            Map.entry("앱", Set.of("앱", "app", "application", "어플", "어플리케이션", "애플리케이션")),
            Map.entry("어플리케이션", Set.of("앱", "app", "application", "어플", "어플리케이션", "애플리케이션")),
            Map.entry("애플리케이션", Set.of("앱", "app", "application", "어플", "어플리케이션", "애플리케이션")),
            Map.entry("앱개발", Set.of("앱개발", "모바일", "mobile", "android", "ios")),

            // 백엔드 / 서버 / 프레임워크
            Map.entry("백엔드", Set.of("백엔드", "backend", "서버", "server")),
            Map.entry("backend", Set.of("백엔드", "backend", "서버", "server")),
            Map.entry("스프링", Set.of("spring", "스프링")),
            Map.entry("스프링부트", Set.of("spring boot", "springboot", "스프링부트", "스프링 부트")),
            Map.entry("자바", Set.of("java", "자바")),
            Map.entry("코틀린", Set.of("kotlin", "코틀린")),
            Map.entry("노드", Set.of("node", "nodejs", "node.js")),

            // 프론트엔드 / 웹
            Map.entry("프론트엔드", Set.of("프론트엔드", "frontend", "front-end")),
            Map.entry("frontend", Set.of("프론트엔드", "frontend", "front-end")),
            Map.entry("리액트", Set.of("react", "리액트")),
            Map.entry("뷰", Set.of("vue", "vuejs")),

            // 인프라 / 클라우드 / 보안
            Map.entry("클라우드", Set.of("cloud", "클라우드", "aws", "gcp", "azure")),
            Map.entry("데브옵스", Set.of("devops", "데브옵스", "ci/cd")),
            Map.entry("쿠버네티스", Set.of("kubernetes", "k8s", "쿠버네티스")),
            Map.entry("보안", Set.of("보안", "security", "정보보안")),

            // 기획 / PM
            Map.entry("pm", Set.of("pm", "프로젝트 매니저", "project manager", "pmo", "기획")),
            Map.entry("기획", Set.of("기획", "planner", "pm", "po")),
            Map.entry("기획자", Set.of("기획자", "기획", "planner", "pm"))
    );

    /**
     * 입력 키워드에 대응하는 동의어/연관어 풀 반환
     * 사전에 없으면 소문자로 변환된 단어 1건 반환
     */
    public Set<String> getSearchPool(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Set.of();
        }
        String normalized = keyword.trim().toLowerCase();
        return SYNONYM_MAP.getOrDefault(normalized, Set.of(normalized));
    }
}