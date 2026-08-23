-- 학적조회(WP-151) — 학적 데모 데이터 시드
--
-- 선행 조건: 2026-08-23_academic_record.sql(스키마) +
-- 2026-08-23_academic_record_common_code_seed.sql(공통코드 시드)이 먼저 실행돼 있어야 한다
-- — 아래 INSERT들이 MAJOR/ACADEMIC_CHANGE_TYPE/ACADEMIC_CHANGE_REASON 코드를 참조한다.
--
-- 이번 티켓엔 학적 데이터를 채우는 쓰기 API가 없다(교직원 수정 API·변동이력 입력 화면
-- 둘 다 후속 티켓, 설계 문서 8-1). 그래서 student_academic_detail + student_academic_change
-- + app_user.academic_status 세 개를 이 스크립트가 한 번에, 4-1장 매핑에 맞춰 일관되게
-- 채운다. 대상은 이미 어디선가 만들어져 있는 user_type='STUDENT' app_user 행 전체다
-- (이 레포엔 회원가입 엔드포인트가 없어 계정 자체는 이 스크립트 밖에서 생성된다).
--
-- 멱등: student_academic_detail 행이 이미 있는 학생은 건너뛴다 — 재실행해도 안전.

-- 1. 대상 학생 목록 + 데모용 순번. 세션 스코프 임시 테이블이라 이 스크립트를 한 번에
--    실행하는 psql 세션 안에서만 유효하고, 세션이 끝나면 자동으로 사라진다.
CREATE TEMP TABLE _academic_seed_target AS
SELECT u.user_id,
       ROW_NUMBER() OVER (ORDER BY u.user_id) AS rn
FROM app_user u
WHERE u.user_type = 'STUDENT'
  AND NOT EXISTS (SELECT 1 FROM student_academic_detail d WHERE d.user_id = u.user_id);

-- 2. student_academic_detail — 시드 대상 MAJOR 8개를 순번으로 돌려가며 배정하고,
--    학년은 1~4를 순번으로 배정한다. 졸업 케이스(rn % 6 = 3, 3번 참고)는 8학기 이수로
--    맞춘다. 생년월일은 20살 전후로 흩어지도록 rn만큼 날짜를 밀어서 생성한다.
INSERT INTO student_academic_detail
    (user_id, major_code_id, grade, gender, birth_date, completed_semesters, created_at, updated_at)
SELECT
    t.user_id,
    m.code_id,
    CASE (t.rn % 4) WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 4 END,
    CASE WHEN t.rn % 2 = 0 THEN 'M' ELSE 'F' END,
    (DATE '2004-03-02' - ((t.rn % 4) * 365 || ' days')::interval - ((t.rn % 30) || ' days')::interval)::date,
    CASE WHEN t.rn % 6 = 3 THEN 8 ELSE ((t.rn % 4) * 2) + 1 END,
    now(), now()
FROM _academic_seed_target t
JOIN LATERAL (
    SELECT code_id
    FROM common_code
    WHERE code_group = 'MAJOR'
    ORDER BY sort_order
    OFFSET (t.rn - 1) % (SELECT count(*) FROM common_code WHERE code_group = 'MAJOR')
    LIMIT 1
) m ON true;

-- 3. student_academic_change — 학생 전원에게 입학(AC100/AR100) 행을 하나씩 넣는다.
--    change_date는 학년 배정과 맞춰 "입학연도 3/2"로 역산한다(2027년 기준 학년도).
INSERT INTO student_academic_change
    (student_id, change_date, change_type_code_id, change_reason_code_id, note, created_by, created_at)
SELECT
    t.user_id,
    make_date(2027 - (CASE (t.rn % 4) WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 4 END), 3, 2),
    (SELECT code_id FROM common_code WHERE code = 'AC100'),
    (SELECT code_id FROM common_code WHERE code = 'AR100'),
    '신입학',
    0, now()
FROM _academic_seed_target t;

-- 4. 나머지 학적변동(휴학/복학/졸업/제적/자퇴) — rn % 6 로 다섯 상태 + 순수 재학
--    케이스를 고르게 섞어 상태별 통계(GET /admin/students/summary)가 다 채워지게 한다.
--    각 분기 끝에서 app_user.academic_status를 4-1장 매핑대로 함께 갱신한다.

-- rn % 6 = 1 → 휴학(AC200/AR300 군휴학), 복학예정 다음 학기
INSERT INTO student_academic_change
    (student_id, change_date, change_type_code_id, change_reason_code_id, military_status,
     scheduled_return_year, scheduled_return_semester_code, created_by, created_at)
SELECT t.user_id, CURRENT_DATE - INTERVAL '30 days',
       (SELECT code_id FROM common_code WHERE code = 'AC200'),
       (SELECT code_id FROM common_code WHERE code = 'AR300'),
       '군휴학', EXTRACT(YEAR FROM CURRENT_DATE)::smallint + 1, 'SPRING', 0, now()
FROM _academic_seed_target t WHERE t.rn % 6 = 1;

UPDATE app_user SET academic_status = '휴학'
WHERE user_id IN (SELECT user_id FROM _academic_seed_target WHERE rn % 6 = 1);

-- rn % 6 = 2 → 휴학 후 복학(AC200 → AC300), 최종 상태는 재학
INSERT INTO student_academic_change
    (student_id, change_date, change_type_code_id, change_reason_code_id, military_status, created_by, created_at)
SELECT t.user_id, CURRENT_DATE - INTERVAL '400 days',
       (SELECT code_id FROM common_code WHERE code = 'AC200'),
       (SELECT code_id FROM common_code WHERE code = 'AR200'),
       NULL, 0, now()
FROM _academic_seed_target t WHERE t.rn % 6 = 2;

INSERT INTO student_academic_change
    (student_id, change_date, change_type_code_id, change_reason_code_id, created_by, created_at)
SELECT t.user_id, CURRENT_DATE - INTERVAL '30 days',
       (SELECT code_id FROM common_code WHERE code = 'AC300'),
       (SELECT code_id FROM common_code WHERE code = 'AR500'),
       0, now()
FROM _academic_seed_target t WHERE t.rn % 6 = 2;

UPDATE app_user SET academic_status = '재학'
WHERE user_id IN (SELECT user_id FROM _academic_seed_target WHERE rn % 6 = 2);

-- rn % 6 = 3 → 졸업(AC400/AR700). student_academic_detail도 학위 정보로 갱신.
INSERT INTO student_academic_change
    (student_id, change_date, change_type_code_id, change_reason_code_id, created_by, created_at)
SELECT t.user_id, CURRENT_DATE - INTERVAL '60 days',
       (SELECT code_id FROM common_code WHERE code = 'AC400'),
       (SELECT code_id FROM common_code WHERE code = 'AR700'),
       0, now()
FROM _academic_seed_target t WHERE t.rn % 6 = 3;

UPDATE student_academic_detail SET degree_name = '학사', degree_no = 'DG-' || user_id, updated_at = now()
WHERE user_id IN (SELECT user_id FROM _academic_seed_target WHERE rn % 6 = 3);

UPDATE app_user SET academic_status = '졸업'
WHERE user_id IN (SELECT user_id FROM _academic_seed_target WHERE rn % 6 = 3);

-- rn % 6 = 4 → 제적(AC500/AR800, 미등록제적)
INSERT INTO student_academic_change
    (student_id, change_date, change_type_code_id, change_reason_code_id, created_by, created_at)
SELECT t.user_id, CURRENT_DATE - INTERVAL '90 days',
       (SELECT code_id FROM common_code WHERE code = 'AC500'),
       (SELECT code_id FROM common_code WHERE code = 'AR800'),
       0, now()
FROM _academic_seed_target t WHERE t.rn % 6 = 4;

UPDATE app_user SET academic_status = '제적'
WHERE user_id IN (SELECT user_id FROM _academic_seed_target WHERE rn % 6 = 4);

-- rn % 6 = 5 → 자퇴(AC600/AR900)
INSERT INTO student_academic_change
    (student_id, change_date, change_type_code_id, change_reason_code_id, created_by, created_at)
SELECT t.user_id, CURRENT_DATE - INTERVAL '120 days',
       (SELECT code_id FROM common_code WHERE code = 'AC600'),
       (SELECT code_id FROM common_code WHERE code = 'AR900'),
       0, now()
FROM _academic_seed_target t WHERE t.rn % 6 = 5;

UPDATE app_user SET academic_status = '자퇴'
WHERE user_id IN (SELECT user_id FROM _academic_seed_target WHERE rn % 6 = 5);

-- rn % 6 = 0 → 순수 재학(입학 행만, 3번에서 이미 넣음). academic_status만 맞춰준다.
UPDATE app_user SET academic_status = '재학'
WHERE user_id IN (SELECT user_id FROM _academic_seed_target WHERE rn % 6 = 0);

DROP TABLE _academic_seed_target;
