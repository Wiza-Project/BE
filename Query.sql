-- 1. pgvector 확장 활성화 (필수)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. ncs_standard 테이블 생성 (DDL)
CREATE TABLE IF NOT EXISTS ncs_standard (
                                            ncs_code_id BIGINT PRIMARY KEY,
                                            ncs_code VARCHAR(20) NOT NULL UNIQUE,
                                            ncs_name VARCHAR(100) NOT NULL,
                                            depth_level INT DEFAULT 1,
                                            parent_code_id BIGINT REFERENCES ncs_standard(ncs_code_id),
                                            description TEXT,
                                            embedding_vector vector(1536), -- 사용하는 모델 차원에 맞게 조정 (예: vector(768))
                                            is_active BOOLEAN DEFAULT TRUE,
                                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                            updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. 표준 NCS 기본 데이터 생성 (SW/정보기술/경영 등 테스트용 대표 코드 적재)
INSERT INTO ncs_standard (
    ncs_code_id, ncs_code, ncs_name, depth_level, parent_code_id, description, is_active, created_at, updated_at
) VALUES
      (100, '20', '정보통신', 1, NULL, '정보기술 및 통신 전반', TRUE, NOW(), NOW()),
      (101, '2001', '정보기술', 2, 100, '소프트웨어 개발 및 IT 인프라', TRUE, NOW(), NOW()),
      (102, '200102', '응용SW엔지니어링', 3, 101, '웹/앱 및 시스템 애플리케이션 개발', TRUE, NOW(), NOW()),
      (200, '02', '경영·회계·사무', 1, NULL, '경영 기획 및 총무 사무', TRUE, NOW(), NOW()),
      (201, '0201', '기획사무', 2, 200, '경영기획 및 사업관리', TRUE, NOW(), NOW())
ON CONFLICT (ncs_code_id) DO NOTHING;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';

SELECT COUNT(*) FROM ncs_standard;

SELECT * FROM ncs_standard;
SELECT * FROM common_code;

SELECT COUNT(*) FROM ncs_standard;
SELECT COUNT(*) FROM common_code;

SELECT count(*) FROM ncs_standard WHERE embedding_vector IS NOT NULL;

DROP TABLE IF EXISTS ncs_standard CASCADE;

CREATE EXTENSION IF NOT EXISTS vector;


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


-- ----------------------------------------------------------------------------------------------------------------------------



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

UPDATE app_user
SET password_hash = '$2a$10$w8T0bVd.wN0uT1cO/v0W.eJ4qK3aX6jN7Z9d1fE8gH5lO2pQrStUu',
    failed_login_count = 0,
    locked_at = NULL,
    account_status = 'ACTIVE'
WHERE university_no = '11111111';