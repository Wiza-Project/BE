package com.gnagnoohc.scms.domain.career.dto.ai;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * career-ai가 지원하는 세부 작성 작업. 클라이언트가 자유 문자열로 작업 지시를 넘기지 못하도록
 * 문구를 서버가 고정한다.
 */
@Getter
@RequiredArgsConstructor
public enum CareerAiTask {

    EXPERIENCE_STAR("이력서에 들어갈 경력·대외활동 한 건을 STAR(상황-과제-행동-결과) 구조의 한국어 문장으로 작성한다."),
    RESUME_SUMMARY("이력서 상단에 들어갈 한 줄 자기소개 요약 문장을 작성한다."),
    PROJECT_DESCRIPTION("포트폴리오 프로젝트 한 건의 소개 문장을 작성한다."),
    PORTFOLIO_SUMMARY("포트폴리오 전체를 소개하는 요약 문장을 작성한다.");

    private final String instruction;
}
