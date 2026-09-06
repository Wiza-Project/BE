-- 1. company_account PK 시퀀스 재동기화 (시퀀스 충돌 방지)
SELECT setval(
               pg_get_serial_sequence('company_account', 'company_account_id'),
               COALESCE((SELECT MAX(company_account_id) FROM company_account), 1)
       );

-- 2. 기업 10개사 주입 블록 실행
DO $$
    DECLARE
        v_staff_id INTEGER;
        i INTEGER;

        v_corp_base_names TEXT[] := ARRAY[
            '네이바클라우드', '카카오엔터프라이즈', '라인플러그', '쿠팡스', '우아한형제들',
            '토스플랫폼', '당근마켓츠', '야놀자스', '원티드랩스', '메가존클라우딩'
            ];
        v_biz_nos TEXT[] := ARRAY[
            '120-81-47521', '220-81-62517', '110-86-12345', '105-87-65432', '214-88-99887',
            '130-86-55443', '211-88-77665', '107-87-33221', '109-86-44332', '220-87-11990'
            ];
        v_ceo_names TEXT[] := ARRAY[
            '김유원', '이경진', '이은정', '강한승', '이국환',
            '이승건', '황도연', '이수진', '이복기', '이주완'
            ];
        v_addresses TEXT[] := ARRAY[
            '경기도 성남시 분당구 분당내곡로 117',
            '경기도 성남시 분당구 판교역로 235',
            '경기도 성남시 분당구 황새울로 360번길 42',
            '서울특별시 송파구 송파대로 570',
            '서울특별시 송파구 위례성대로 2',
            '서울특별시 강남구 테헤란로 142',
            '서울특별시 서초구 강남대로 465',
            '서울특별시 강남구 테헤란로 108',
            '서울특별시 송파구 올림픽로 300',
            '서울특별시 강남구 논현로 648'
            ];

        v_corp_name VARCHAR(200);
    BEGIN
        -- 1. 취창업부서 교직원(88888888) 식별자 추출
        SELECT user_id INTO v_staff_id
        FROM app_user
        WHERE university_no = '88888888' OR email = 'career_admin1@univ.ac.kr'
        LIMIT 1;

        IF v_staff_id IS NULL THEN
            RAISE EXCEPTION '취창업부서 교직원 계정을 찾을 수 없습니다.';
        END IF;

        -- 2. 기업 10개사 등록
        FOR i IN 1..10 LOOP
                v_corp_name := v_corp_base_names[i] || i::text;

                INSERT INTO company_account (
                    company_name,
                    business_registration_no,
                    login_id,
                    password_hash,
                    representative_name,
                    contact_name,
                    contact_phone,
                    contact_email,
                    address,
                    verification_status,
                    verified_by,
                    verified_at,
                    account_status,
                    created_at,
                    updated_at
                ) VALUES (
                             v_corp_name,
                             v_biz_nos[i],
                             'company_recruiter_' || i,
                             '$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNO',
                             v_ceo_names[i],
                             v_corp_name || ' 채용담당자',
                             '02-555-' || LPAD(i::text, 4, '0'),
                             'recruit' || i || '@testcorp' || i || '.local',
                             v_addresses[i],
                             'VERIFIED',
                             v_staff_id,
                             NOW() - (INTERVAL '5 day' * (11 - i)),
                             'ACTIVE',
                             NOW() - (INTERVAL '10 day' * (11 - i)),
                             NOW()
                         )
                ON CONFLICT (business_registration_no) DO UPDATE
                    SET verified_by = v_staff_id,
                        verification_status = 'VERIFIED',
                        account_status = 'ACTIVE',
                        updated_at = NOW();
            END LOOP;

        RAISE NOTICE '성공: 취창업 교직원(ID: %)이 승인 완료한 기업 10개사가 정상 등록되었습니다.', v_staff_id;
    END $$;