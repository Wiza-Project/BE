-- 마일리지 개발 확인용 학생·교직원 계정 시드
--
-- 로그인 정보
--   학생   : 20260001 / 1234
--   교직원 : 20260002 / 1234
--
-- 회원가입 API가 없는 프로젝트이므로 로컬 DB에서 수동 실행한다.
-- 비밀번호는 평문으로 저장하지 않고 PostgreSQL pgcrypto의 bcrypt로 해시한다.
-- 같은 university_no가 이미 있으면 기존 계정은 변경하지 않는다.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM common_code
        WHERE code_group = 'DEPARTMENT'
          AND code = 'D100'
    ) THEN
        RAISE EXCEPTION 'DEPARTMENT D100 코드가 없습니다. 공통코드 시드를 먼저 실행하세요.';
    END IF;
END
$$;

INSERT INTO app_user (
    department_code_id,
    university_no,
    user_type,
    password_hash,
    user_name,
    email,
    phone,
    preferred_contact,
    academic_status,
    account_status,
    failed_login_count,
    locked_at,
    last_login_at,
    created_by,
    created_at,
    updated_at
)
VALUES
(
    NULL,
    '20260001',
    'STUDENT',
    crypt('1234', gen_salt('bf')),
    '마일리지 테스트 학생',
    NULL,
    NULL,
    NULL,
    '재학',
    'ACTIVE',
    0,
    NULL,
    NULL,
    0,
    now(),
    now()
),
(
    (SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D100'),
    '20260002',
    'STAFF',
    crypt('1234', gen_salt('bf')),
    '마일리지 테스트 교직원',
    NULL,
    NULL,
    NULL,
    NULL,
    'ACTIVE',
    0,
    NULL,
    NULL,
    0,
    now(),
    now()
)
ON CONFLICT (university_no) DO NOTHING;

INSERT INTO user_role (user_id, role_code, granted_by, granted_at)
SELECT user_id, 'SD100', 0, now()
FROM app_user
WHERE university_no = '20260001'
ON CONFLICT (user_id, role_code) DO NOTHING;

INSERT INTO user_role (user_id, role_code, granted_by, granted_at)
SELECT user_id, 'ST100', 0, now()
FROM app_user
WHERE university_no = '20260002'
ON CONFLICT (user_id, role_code) DO NOTHING;

COMMIT;
