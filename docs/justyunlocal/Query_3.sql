-- INSERT INTO consent_policy
-- (consent_type, module_code, version, title, content,
--  is_required, effective_from, effective_to, is_active, created_by, created_at)
-- VALUES
-- -- COMMON
-- ('TERMS_OF_SERVICE', 'COMMON', '2026.1', '이용약관', '학생통합역량관리시스템 이용약관 본문...', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
-- ('PERSONAL_INFO', 'COMMON', '2026.1', '개인정보 수집·이용 동의', E'■ 수집 항목\n- 필수: 학번/교번, 성명, 연락처\n■ 보유 기간\n- 5년', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
--
-- -- ASSESSMENT
-- ('PERSONAL_INFO', 'ASSESSMENT', '2026.1', '핵심역량진단 개인정보 수집·이용 동의', '핵심역량진단 수집 동의 본문...', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
-- ('SENSITIVE_INFO', 'ASSESSMENT', '2026.1', '핵심역량진단 민감정보 처리 동의', '핵심역량진단 민감정보 본문...', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
--
-- -- COUNSELING
-- ('PERSONAL_INFO', 'COUNSELING', '2026.1', '상담 개인정보 수집·이용 동의', '상담 개인정보 동의 본문...', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
--
-- -- CAREER (AI 매칭 핵심 항목)
-- ('PERSONAL_INFO', 'CAREER', '2026.1', '취·창업 서비스 개인정보 수집·이용 동의', '취창업 서비스 이용 동의...', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
-- ('PROFILING', 'CAREER', '2026.1', 'AI 맞춤형 채용공고 추천 활용 동의(선택)', E'■ 활용 항목\n- 희망직무/선호조건, 보유 역량 요약\n■ 처리 목적\n- AI 기반 학생 맞춤형 채용공고 추천 연산', false, '2026-08-24T00:00:00Z', null, true, 1, now()),
-- ('THIRD_PARTY_SHARE', 'CAREER', '2026.1', '채용기업 개인정보 제3자 제공 동의', '채용기업 제3자 제공 동의...', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
--
-- -- PROGRAM
-- ('TERMS_OF_SERVICE', 'PROGRAM', '2026.1', '비교과 프로그램 이용약관', '비교과 이용약관 본문...', true, '2026-08-24T00:00:00Z', null, true, 1, now()),
-- ('PERSONAL_INFO', 'PROGRAM', '2026.1', '비교과 개인정보 수집·이용 동의', '비교과 개인정보 동의...', true, '2026-08-24T00:00:00Z', null, true, 1, now())
-- ON CONFLICT (consent_type, module_code, version) DO NOTHING;




-- INSERT INTO user_consent (user_id, consent_policy_id, consented_at, created_at)
-- SELECT
--     10,
--     consent_policy_id,
--     NOW(),
--     NOW()
-- FROM consent_policy
-- WHERE module_code = 'CAREER'
--   AND consent_type = 'PROFILING'
--   AND is_active = true
-- LIMIT 1
-- ON CONFLICT DO NOTHING;




-- 1. 학생 10번이 실제로 student_profile 테이블에 존재하는가?
-- SELECT user_id, student_grade, (embedding_vector IS NOT NULL) AS has_vector
-- FROM student_profile
-- WHERE user_id = 10;

-- INSERT INTO student_profile (user_id, student_grade)
-- VALUES (10, 3)
-- ON CONFLICT (user_id) DO NOTHING;

-- 2. 학생이 희망조건으로 선택한 common_code와 ncs_standard가 서로 연결되는가?
-- SELECT
--     jp.student_id,
--     jp.ncs_code_id,
--     cc.code AS common_code_값,
--     cc.code_name AS 직무명,
--     ns.ncs_code AS ncs_표준코드,
--     (ns.embedding_vector IS NOT NULL) AS ncs벡터유무
-- FROM job_preference jp
--          JOIN common_code cc ON jp.ncs_code_id = cc.code_id
--          LEFT JOIN ncs_standard ns ON cc.code = ns.ncs_code
-- WHERE jp.student_id = 10;

-- [확인 1] 학생 10번의 희망조건 테이블에 무엇이 들어있는가?
-- SELECT * FROM job_preference WHERE student_id = 10;
--
-- -- [확인 2] common_code에 NCS 관련 코드가 어떻게 들어가 있는가? (상위 5건)
-- SELECT code_id, code_group, code, code_name
-- FROM common_code
-- WHERE code_group = 'NCS_CODE'
-- LIMIT 5;
--
-- -- [확인 3] ncs_standard에는 ncs_code가 어떤 형식으로 들어가 있는가? (상위 5건)
-- SELECT ncs_code, ncs_name, (embedding_vector IS NOT NULL) AS has_vector
-- FROM ncs_standard
-- LIMIT 5;

DO $$
    DECLARE
        v_admin_id INTEGER;
        v_ncs_code VARCHAR(50);
        v_ncs_name VARCHAR(255);
        v_vector vector;
        v_common_code_id INTEGER;
        v_student_id INTEGER := 10;
    BEGIN
        -- 1. created_by에 넣을 교직원/관리자 ID 확보
        SELECT user_id INTO v_admin_id
        FROM app_user
        WHERE university_no IN ('99999999', '88888888') OR user_id = 1
        ORDER BY CASE
                     WHEN university_no = '99999999' THEN 1
                     WHEN university_no = '88888888' THEN 2
                     ELSE 3
                     END
        LIMIT 1;

        IF v_admin_id IS NULL THEN
            SELECT user_id INTO v_admin_id FROM app_user LIMIT 1;
        END IF;

        -- 2. ncs_standard 원장에서 실제 벡터가 들어있는 첫 번째 NCS 데이터 추출
        SELECT ncs_code, category_name, embedding_vector
        INTO v_ncs_code, v_ncs_name, v_vector
        FROM ncs_standard
        WHERE embedding_vector IS NOT NULL
        LIMIT 1;

        IF v_ncs_code IS NULL THEN
            RAISE EXCEPTION 'ncs_standard 테이블에 임베딩 벡터를 가진 데이터가 없습니다!';
        END IF;

        -- 3. common_code 테이블에 해당 ncs_code(8자리)와 완전히 일치하는 NCS_CODE 행 확보
        SELECT code_id INTO v_common_code_id
        FROM common_code
        WHERE code_group = 'NCS_CODE' AND code = v_ncs_code
        LIMIT 1;

        IF v_common_code_id IS NULL THEN
            INSERT INTO common_code (
                code_group, code, code_name, sort_order, is_active, created_by, created_at, updated_at
            ) VALUES (
                         'NCS_CODE',
                         v_ncs_code,
                         COALESCE(v_ncs_name, 'IT/소프트웨어'),
                         1,
                         true,
                         v_admin_id,
                         NOW(),
                         NOW()
                     ) RETURNING code_id INTO v_common_code_id;
        END IF;

        -- 4. 학생(10)의 취업 희망조건에 해당 공통코드 ID 연결
        INSERT INTO job_preference (
            student_id, ncs_code_id, preferred_employment_type, minimum_salary, created_at, updated_at
        ) VALUES (
                     v_student_id, v_common_code_id, 'REGULAR', 3600, NOW(), NOW()
                 )
        ON CONFLICT (student_id) DO UPDATE
            SET ncs_code_id = v_common_code_id, updated_at = NOW();

        -- 5. student_profile에 해당 ncs_standard의 실제 벡터 주입
        INSERT INTO student_profile (
            user_id, embedding_vector, student_grade
        ) VALUES (
                     v_student_id, v_vector, 3
                 )
        ON CONFLICT (user_id) DO UPDATE
            SET embedding_vector = v_vector;

        RAISE NOTICE '동기화 완료: ncs_code(%)의 벡터가 학생(10)의 student_profile에 반영되었습니다.', v_ncs_code;
    END $$;