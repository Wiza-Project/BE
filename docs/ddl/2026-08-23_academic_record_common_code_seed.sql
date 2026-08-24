-- 학적조회(WP-151) — 공통코드 시드: MAJOR / ACADEMIC_CHANGE_TYPE / ACADEMIC_CHANGE_REASON
--
-- 선행 조건: 2026-08-23_academic_record.sql(스키마)이 먼저 실행돼 있어야 한다
-- (student_academic_detail.major_code_id / student_academic_change.change_type_code_id·
-- change_reason_code_id가 이 시드가 만드는 행을 참조하므로 순서 자체는 상관없지만,
-- 이 파일만 따로 재실행할 일도 있으니 스키마가 먼저 있다고 가정하는 편이 안전하다).
--
-- 로컬은 CommonCodeSeeder(local 프로필 CommandLineRunner)가 이 시드를 자동으로 반영하지만,
-- 운영 배포 시엔 이 파일을 직접 실행해야 한다. CommonCodeSeeder.SEEDS 와 내용이 반드시
-- 같아야 한다(수동 동기화 — 자동 아님).
--
-- 멱등: (code_group, code) 유니크 제약(uq_common_code_group_code)에 기대어
-- ON CONFLICT DO NOTHING을 쓰므로 여러 번 실행해도 안전하다.

-- 소속학과(MAJOR). code/sort_order는 전체 85개 가나다순 목록(entity-plan.md 부록 A)
-- 기준 위치를 그대로 쓴다 — 지금은 데모 학과 8개만 시드하지만 나머지 80개를 나중에
-- 채워도 코드값이 어긋나지 않는다.
INSERT INTO common_code
    (code_group, code, code_name, sort_order, is_active, created_by, created_at, updated_at)
VALUES
    ('MAJOR', 'MJ400',  '경영학부',        4,  true, 0, now(), now()),
    ('MAJOR', 'MJ3200', '산업공학과',      32, true, 0, now(), now()),
    ('MAJOR', 'MJ4400', '심리학과',        44, true, 0, now(), now()),
    ('MAJOR', 'MJ5100', '영어영문학과',    51, true, 0, now(), now()),
    ('MAJOR', 'MJ6400', '전기·정보공학부', 64, true, 0, now(), now()),
    ('MAJOR', 'MJ8000', '컴퓨터공학부',    80, true, 0, now(), now()),
    ('MAJOR', 'MJ8100', '통계학과',        81, true, 0, now(), now()),
    ('MAJOR', 'MJ8500', '화학생물공학부',  85, true, 0, now(), now())
ON CONFLICT (code_group, code) DO NOTHING;

-- 학적변동코드(ACADEMIC_CHANGE_TYPE)
INSERT INTO common_code
    (code_group, code, code_name, sort_order, is_active, created_by, created_at, updated_at)
VALUES
    ('ACADEMIC_CHANGE_TYPE', 'AC100', '입학', 1, true, 0, now(), now()),
    ('ACADEMIC_CHANGE_TYPE', 'AC200', '휴학', 2, true, 0, now(), now()),
    ('ACADEMIC_CHANGE_TYPE', 'AC300', '복학', 3, true, 0, now(), now()),
    ('ACADEMIC_CHANGE_TYPE', 'AC400', '졸업', 4, true, 0, now(), now()),
    ('ACADEMIC_CHANGE_TYPE', 'AC500', '제적', 5, true, 0, now(), now()),
    ('ACADEMIC_CHANGE_TYPE', 'AC600', '자퇴', 6, true, 0, now(), now())
ON CONFLICT (code_group, code) DO NOTHING;

-- 학적변동사유(ACADEMIC_CHANGE_REASON). parent_code_id로 위 ACADEMIC_CHANGE_TYPE에 종속.
INSERT INTO common_code
    (code_group, code, code_name, sort_order, parent_code_id, is_active, created_by, created_at, updated_at)
VALUES
    ('ACADEMIC_CHANGE_REASON', 'AR100', '신입학',     1, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC100'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR200', '일반휴학',   2, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC200'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR300', '군휴학',     3, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC200'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR400', '질병휴학',   4, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC200'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR500', '일반복학',   5, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC300'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR600', '군복학',     6, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC300'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR700', '졸업',       7, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC400'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR800', '미등록제적', 8, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC500'), true, 0, now(), now()),
    ('ACADEMIC_CHANGE_REASON', 'AR900', '자퇴',       9, (SELECT code_id FROM common_code WHERE code_group = 'ACADEMIC_CHANGE_TYPE' AND code = 'AC600'), true, 0, now(), now())
ON CONFLICT (code_group, code) DO NOTHING;
