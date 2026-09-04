SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';

SELECT COUNT(*) FROM ncs_standard;
SELECT COUNT(*) FROM job_posting;

-- CAREER PROFILING 정책과 학생 동의가 제대로 체결되었는지 확인
SELECT
    uc.user_consent_id,
    uc.user_id,
    cp.module_code,
    cp.consent_type,
    cp.title,
    uc.consented_at
FROM user_consent uc
         JOIN consent_policy cp ON uc.consent_policy_id = cp.consent_policy_id
WHERE uc.user_id = 10 AND cp.module_code = 'CAREER';

SELECT * FROM ncs_standard;
SELECT * FROM common_code;

SELECT COUNT(*) FROM ncs_standard;
SELECT COUNT(*) FROM common_code;

SELECT count(*) FROM ncs_standard WHERE embedding_vector IS NOT NULL;

DROP TABLE IF EXISTS ncs_standard CASCADE;

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE company_account ALTER COLUMN login_id DROP NOT NULL;
ALTER TABLE company_account ALTER COLUMN password_hash DROP NOT NULL;

-- 1. 기존 권한 및 유저 데이터 정리 (FK 제약 순서 준수)
DELETE FROM user_role WHERE user_id IN (10, 11, 12);
-- 1. app_user 데이터 삭제
DELETE FROM app_user WHERE user_id IN (10, 11, 12) OR university_no IN ('11111111', '55555555', '99999999');

-- 2. 유저 데이터 생성 (비밀번호: 1)
INSERT INTO app_user (
    user_id, created_at, updated_at, academic_status, account_status, created_by,
    email, failed_login_count, last_login_at, locked_at, password_hash,
    phone, preferred_contact, university_no, user_name, user_type, department_code_id
) VALUES
      (
          10, NOW(), NOW(), 'ENROLLED', 'ACTIVE', NULL,
          'student1@univ.ac.kr', 0, NOW(), NULL, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
          '010-1111-1111', 'EMAIL', '11111111', '김학생', 'STUDENT', NULL
      ),
      (
          11, NOW(), NOW(), NULL, 'ACTIVE', NULL,
          'staff1@univ.ac.kr', 0, NOW(), NULL, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
          '010-5555-5555', 'EMAIL', '55555555', '이교직', 'STAFF', NULL
      ),
      (
          12, NOW(), NOW(), NULL, 'ACTIVE', NULL,
          'staff2@univ.ac.kr', 0, NOW(), NULL, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
          '010-9999-9999', 'EMAIL', '99999999', '박센터', 'STAFF', NULL
      );

-- 3. 권한 매핑 생성
INSERT INTO user_role (user_id, role_code, granted_at) VALUES
                                                           (10, 'ROLE_STUDENT', NOW()),
                                                           (11, 'ROLE_STAFF', NOW()),
                                                           (11, 'ROLE_ADMIN', NOW()),
                                                           (12, 'ROLE_STAFF', NOW());
