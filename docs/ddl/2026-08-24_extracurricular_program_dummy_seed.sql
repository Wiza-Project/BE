-- 비교과 프로그램(extracurricular_program) QA용 더미 데이터 — WP-159
--
-- WP-159에서 추가된 두 검색 기능을 QA하기 위한 더미 프로그램 6건.
-- 로컬/개발 환경에서 수동 실행하세요. (docs/ddl/2026-08-20_common_code_seed.sql,
-- 2026-08-21_competency_seed.sql과 동일한 방식 — 별도 시더 클래스 없음)
--
-- 전제조건: 아래 값들이 이미 DB에 존재해야 합니다.
--   - common_code: PROGRAM_TYPE(PT100..PT600), DEPARTMENT(D100..D400) — CommonCodeSeeder
--   - competency: C100..C600 — 2026-08-21_competency_seed.sql
--   - app_user: user_type='STAFF' 계정 최소 1건 (관리자 화면에서 직접 가입한 계정 등)
-- 하나라도 없으면 FK 서브쿼리가 NULL이 되어 INSERT가 NOT NULL 제약 위반으로 실패합니다.
--
-- 멱등: program_name에는 유니크 제약이 없어 ON CONFLICT를 쓸 수 없으므로,
-- 각 INSERT를 WHERE NOT EXISTS 가드로 감싸 여러 번 실행해도 중복 삽입되지 않게 함.
--
-- 검색 시나리오 매핑:
--   1) 이름에만 키워드("AI") 포함, 설명엔 없음        → 이름 검색 매칭 케이스
--   2) 설명에만 키워드("머신러닝") 포함, 이름엔 없음   → description 통합검색 핵심 검증 케이스
--   3) 이름·설명 둘 다 무관한 키워드                  → 매칭 안 되는 대조군
--   4) 모집 마감 + 운영중(OPERATING) 상태
--   5) 모집/운영 모두 종료(CLOSED) 상태
--   6) 이름에도 "머신러닝" 포함 → 2)와 함께 이름매칭/설명매칭 구분 검증용

INSERT INTO extracurricular_program
    (operating_unit_code_id, program_type_code_id, competency_id, manager_user_id,
     program_name, description,
     recruitment_starts_at, recruitment_ends_at, operation_starts_at, operation_ends_at,
     capacity, completion_rate, program_status, created_at, updated_at)
SELECT
    (SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D200'),
    (SELECT code_id FROM common_code WHERE code_group = 'PROGRAM_TYPE' AND code = 'PT100'),
    (SELECT competency_id FROM competency WHERE competency_code = 'C100'),
    (SELECT user_id FROM app_user WHERE user_type = 'STAFF' ORDER BY user_id LIMIT 1),
    'AI 프로그래밍 캠프',
    '파이썬 기초 문법부터 실습 프로젝트까지 다루는 입문자 대상 캠프입니다.',
    now() - interval '5 days', now() + interval '10 days',
    now() + interval '15 days', now() + interval '45 days',
    30, 80, 'DRAFT', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM extracurricular_program WHERE program_name = 'AI 프로그래밍 캠프');

INSERT INTO extracurricular_program
    (operating_unit_code_id, program_type_code_id, competency_id, manager_user_id,
     program_name, description,
     recruitment_starts_at, recruitment_ends_at, operation_starts_at, operation_ends_at,
     capacity, completion_rate, program_status, created_at, updated_at)
SELECT
    (SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D100'),
    (SELECT code_id FROM common_code WHERE code_group = 'PROGRAM_TYPE' AND code = 'PT600'),
    (SELECT competency_id FROM competency WHERE competency_code = 'C300'),
    (SELECT user_id FROM app_user WHERE user_type = 'STAFF' ORDER BY user_id LIMIT 1),
    '글로벌 리더십 워크숍',
    '해외 파트너 대학과 함께하는 리더십 세션으로, 데이터 분석 실습과 머신러닝 기초를 다루는 특강이 포함되어 있습니다.',
    now() - interval '3 days', now() + interval '12 days',
    now() + interval '20 days', now() + interval '50 days',
    25, 80, 'DRAFT', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM extracurricular_program WHERE program_name = '글로벌 리더십 워크숍');

INSERT INTO extracurricular_program
    (operating_unit_code_id, program_type_code_id, competency_id, manager_user_id,
     program_name, description,
     recruitment_starts_at, recruitment_ends_at, operation_starts_at, operation_ends_at,
     capacity, completion_rate, program_status, created_at, updated_at)
SELECT
    (SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D400'),
    (SELECT code_id FROM common_code WHERE code_group = 'PROGRAM_TYPE' AND code = 'PT300'),
    (SELECT competency_id FROM competency WHERE competency_code = 'C500'),
    (SELECT user_id FROM app_user WHERE user_type = 'STAFF' ORDER BY user_id LIMIT 1),
    '대학생 창업 아이디어 공모전',
    '팀을 이루어 사업 아이디어를 기획하고 발표하는 교내 공모전입니다.',
    now() - interval '2 days', now() + interval '20 days',
    now() + interval '25 days', now() + interval '60 days',
    50, 80, 'DRAFT', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM extracurricular_program WHERE program_name = '대학생 창업 아이디어 공모전');

INSERT INTO extracurricular_program
    (operating_unit_code_id, program_type_code_id, competency_id, manager_user_id,
     program_name, description,
     recruitment_starts_at, recruitment_ends_at, operation_starts_at, operation_ends_at,
     capacity, completion_rate, program_status, created_at, updated_at)
SELECT
    (SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D200'),
    (SELECT code_id FROM common_code WHERE code_group = 'PROGRAM_TYPE' AND code = 'PT500'),
    (SELECT competency_id FROM competency WHERE competency_code = 'C400'),
    (SELECT user_id FROM app_user WHERE user_type = 'STAFF' ORDER BY user_id LIMIT 1),
    '교내 봉사활동 오리엔테이션',
    '학기 중 진행되는 교내 봉사활동 참여를 위한 사전 오리엔테이션 프로그램입니다.',
    now() - interval '20 days', now() - interval '5 days',
    now() - interval '3 days', now() + interval '10 days',
    40, 80, 'OPERATING', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM extracurricular_program WHERE program_name = '교내 봉사활동 오리엔테이션');

INSERT INTO extracurricular_program
    (operating_unit_code_id, program_type_code_id, competency_id, manager_user_id,
     program_name, description,
     recruitment_starts_at, recruitment_ends_at, operation_starts_at, operation_ends_at,
     capacity, completion_rate, program_status, created_at, updated_at)
SELECT
    (SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D300'),
    (SELECT code_id FROM common_code WHERE code_group = 'PROGRAM_TYPE' AND code = 'PT400'),
    (SELECT competency_id FROM competency WHERE competency_code = 'C200'),
    (SELECT user_id FROM app_user WHERE user_type = 'STAFF' ORDER BY user_id LIMIT 1),
    '겨울방학 심리상담 프로그램',
    '방학 기간 학생 정서 지원을 위한 1:1 심리상담 프로그램입니다.',
    now() - interval '60 days', now() - interval '40 days',
    now() - interval '35 days', now() - interval '10 days',
    20, 80, 'CLOSED', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM extracurricular_program WHERE program_name = '겨울방학 심리상담 프로그램');

INSERT INTO extracurricular_program
    (operating_unit_code_id, program_type_code_id, competency_id, manager_user_id,
     program_name, description,
     recruitment_starts_at, recruitment_ends_at, operation_starts_at, operation_ends_at,
     capacity, completion_rate, program_status, created_at, updated_at)
SELECT
    (SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D100'),
    (SELECT code_id FROM common_code WHERE code_group = 'PROGRAM_TYPE' AND code = 'PT100'),
    (SELECT competency_id FROM competency WHERE competency_code = 'C600'),
    (SELECT user_id FROM app_user WHERE user_type = 'STAFF' ORDER BY user_id LIMIT 1),
    '머신러닝 실전 프로젝트 캠프',
    '팀 프로젝트를 통해 모델을 직접 구현해보는 심화 캠프입니다.',
    now() - interval '1 days', now() + interval '14 days',
    now() + interval '18 days', now() + interval '48 days',
    20, 80, 'DRAFT', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM extracurricular_program WHERE program_name = '머신러닝 실전 프로젝트 캠프');
