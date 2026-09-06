DO $$
    DECLARE
        v_staff_id INTEGER;
        v_dept_id INTEGER;
        v_policy_record RECORD;
        -- 비밀번호 '1'의 BCrypt 해시값
        v_pw_hash VARCHAR(255) := '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.';
    BEGIN
        -- 1. 취창업지원부서 부서코드 탐색 (없으면 1건 할당)
        SELECT code_id INTO v_dept_id
        FROM common_code
        WHERE code_group = 'DEPARTMENT_CODE'
          AND (code_name LIKE '%취%' OR code_name LIKE '%창업%' OR code_name LIKE '%학생%')
        LIMIT 1;

        IF v_dept_id IS NULL THEN
            SELECT code_id INTO v_dept_id FROM common_code WHERE code_group = 'DEPARTMENT_CODE' LIMIT 1;
        END IF;

        -- 2. 교직원 계정 생성 또는 업데이트 (ID: 88888888 / PW: 1, failed_login_count: 0)
        SELECT user_id INTO v_staff_id
        FROM app_user
        WHERE university_no = '88888888';

        IF v_staff_id IS NULL THEN
            INSERT INTO app_user (
                created_at,
                updated_at,
                account_status,
                email,
                password_hash,
                phone,
                user_name,
                user_type,
                department_code_id,
                university_no,
                failed_login_count
            ) VALUES (
                         NOW(),
                         NOW(),
                         'ACTIVE',
                         'career_staff_88888888@univ.ac.kr',
                         v_pw_hash,
                         '010-8888-8888',
                         '취창업지원팀 교직원1',
                         'EMPLOYEE',
                         v_dept_id,
                         '88888888',
                         0
                     ) RETURNING user_id INTO v_staff_id;

            RAISE NOTICE '취창업부서 교직원 계정 신규 생성 (user_id: %, university_no: 88888888)', v_staff_id;
        ELSE
            UPDATE app_user
            SET password_hash = v_pw_hash,
                account_status = 'ACTIVE',
                failed_login_count = 0,
                updated_at = NOW()
            WHERE user_id = v_staff_id;

            RAISE NOTICE '기존 취창업부서 교직원 계정 업데이트 완료 (user_id: %)', v_staff_id;
        END IF;

        -- 3. 교직원 역할 부여
        INSERT INTO user_role (role_code, granted_at, granted_by, user_id)
        VALUES
            ('ROLE_STAFF', NOW(), v_staff_id, v_staff_id),
            ('ROLE_CAREER_ADMIN', NOW(), v_staff_id, v_staff_id)
        ON CONFLICT (role_code, user_id) DO NOTHING;

        -- 4. 공통/취창업 활성 약관 동의 이력 적재
        FOR v_policy_record IN
            SELECT consent_policy_id
            FROM consent_policy
            WHERE is_active = true
              AND (module_code = 'COMMON' OR module_code = 'CAREER')
            LOOP
                IF NOT EXISTS (
                    SELECT 1 FROM user_consent
                    WHERE user_id = v_staff_id
                      AND consent_policy_id = v_policy_record.consent_policy_id
                      AND withdrawn_at IS NULL
                ) THEN
                    INSERT INTO user_consent (
                        user_id,
                        consent_policy_id,
                        consented_at,
                        withdrawn_at,
                        created_at
                    ) VALUES (
                                 v_staff_id,
                                 v_policy_record.consent_policy_id,
                                 NOW(),
                                 NULL,
                                 NOW()
                             );
                END IF;
            END LOOP;

        RAISE NOTICE '성공: 아이디 88888888 (비밀번호 1) 교직원 계정 설정 완료.';
    END $$;