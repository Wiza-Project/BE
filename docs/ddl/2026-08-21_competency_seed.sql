-- 핵심역량(competency) 초기 시드 데이터 — 최상위 6개 역량
--
-- 로컬/개발 환경에서 수동 실행하세요. 별도의 CommandLineRunner/시더 클래스는 두지 않기로
-- 결정함 (공통코드처럼 앱 기동 시 자동 반영해야 할 만큼 자주 바뀌는 데이터가 아님).
--
-- 멱등: competency_code의 유니크 제약에 기대어 ON CONFLICT DO NOTHING을 쓰므로
-- 여러 번 실행해도 안전합니다.
--
-- 역량코드/축순서는 PROGRAM_TYPE(PT100..), DEPARTMENT(D100..) 공통코드와 동일한
-- 접두어+100단위 컨벤션(C100..C600)을 따름 — 세분류를 C150처럼 중간에 끼워넣을 여유를 둠.
-- english_name/description은 원본 소스(핵심역량 및 하위역량.xlsx)에 없어 NULL로 둠 —
-- 임의로 채우지 않음.
--
-- 문항(assessment_question, 90건)은 별도 기능(WP-74, 엑셀 업로드 API)으로 적재 예정이라
-- 이 파일 범위에서 제외.

INSERT INTO competency
    (parent_competency_id, competency_code, competency_name, english_name, description,
     display_order, is_active, created_by, created_at, updated_at)
VALUES
    (NULL, 'C100', '자기관리 역량',           NULL, NULL, 100, true, 0, now(), now()),
    (NULL, 'C200', '의사소통 역량',           NULL, NULL, 200, true, 0, now(), now()),
    (NULL, 'C300', '글로벌 역량',             NULL, NULL, 300, true, 0, now(), now()),
    (NULL, 'C400', '대인관계 역량',           NULL, NULL, 400, true, 0, now(), now()),
    (NULL, 'C500', '종합적 사고역량',         NULL, NULL, 500, true, 0, now(), now()),
    (NULL, 'C600', '자원·정보·기술 활용 역량', NULL, NULL, 600, true, 0, now(), now())
ON CONFLICT (competency_code) DO NOTHING;
