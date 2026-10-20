/**
 * <h2>비교과프로그램 (Part 1)</h2>
 *
 * <h3>P1100 비교과프로그램 등록 — Owner: 학생역량센터</h3>
 * <pre>
 * 프로그램분류 설정                      비교과프로그램 등록
 * ───────────────────────────────────────────────────────────
 * 학생역량센터   분류체계관리 ──────────▶ 승인
 *                     ▲                    ▲
 * 진로상담센터        │                    │
 * 취업지원센터   분류체계 요청        비교과프로그램 신청
 * 공학교육혁신센터
 * 학과/학부
 * 학생지원센터
 * </pre>
 * DATA: 프로그램분류, 프로그램정보
 *
 * <h3>P1200 비교과프로그램 운영 — Owner: 비교과운영부서</h3>
 * <pre>
 * 참여신청                              운영결과
 * ───────────────────────────────────────────────────────────
 * 학생역량센터                          운영현황 조회
 * 비교과운영부서 참여승인 ──▶ 결과등록 ──▶ 이수여부 결정 ──▶ 수료증 출력
 * 학생          참여신청                 참여결과 조회
 * </pre>
 * DATA: 프로그램참여신청, 프로그램정보, 학생정보, 마일리지점수
 *
 * <h3>구현 체크리스트</h3>
 * <ul>
 *   <li>[ ] ProgramCategory — 계층 구조(대분류/중분류)라면 self-reference 또는 depth 컬럼 설계 필요</li>
 *   <li>[ ] CategoryRequest — 부서의 분류체계 요청 + 학생역량센터 승인 ({@code ApprovalStatus} 재사용)</li>
 *   <li>[ ] Program — 모집 인원, 신청 기간, 운영 기간, 운영부서, 부여 마일리지</li>
 *   <li>[x] ProgramApplication — 학생 참여신청 (정원 초과 시 대기순번 자동 부여). 참여승인/거절 단계는 미구현 ({@code ApprovalStatus} 재사용 예정)</li>
 *   <li>[ ] ProgramResult — 이수 여부, 출석/성과 기록</li>
 *   <li>[ ] 수료증 PDF 생성 (openpdf). 대학 로고·직인 이미지 필요</li>
 *   <li>[x] <b>이수 확정 시 마일리지 자동 적립</b> — mileage 패키지를 직접 호출하지 말고
 *           Spring 의 {@code ApplicationEventPublisher} 로 이벤트를 발행하세요.
 *           도메인 간 양방향 의존이 생기면 이후 수정이 매우 어려워집니다.
 *           ({@code ProgramStatusScheduler}가 {@code ProgramCompletionJudgedEvent}를 발행하는 부분까지가
 *           program 도메인 책임이며, 이를 구독해 실제 적립을 수행하는 리스너는 마일리지 도메인 담당.)</li>
 *   <li>[x] <b>이수 확정 시 이력서 연동 이벤트 발행</b> — 위와 같은 이유로 취창업 도메인을 직접 호출하지
 *           않고 {@code ProgramStatusScheduler}가 {@code ExtracurricularActivityCompletedEvent}를
 *           발행하는 부분까지가 program 도메인 책임이며, 이를 구독해 이력서 읽기 모델에 upsert하는
 *           리스너는 취창업 도메인 담당.</li>
 *   <li>[ ] 핵심역량과의 매핑 — P2200 의 "추천 비교과프로그램 조회"를 위해
 *           Program ↔ Competency 다대다 매핑 테이블이 필요합니다.</li>
 * </ul>
 */
package com.gnagnoohc.scms.domain.program;
