DO $$
    DECLARE
        v_company_id INTEGER;
        v_region_ids INTEGER[];
        v_region_count INTEGER;

        v_ncs_biz_id INTEGER;
        v_ncs_sw_id INTEGER;
        v_ncs_default_id INTEGER;

        i INTEGER;
        v_reg_id INTEGER;
        v_emp_type VARCHAR(30);
        v_post_type VARCHAR(30);
        v_benefit VARCHAR(30);
        v_salary VARCHAR(100);
        v_title VARCHAR(250);
        v_desc TEXT;
        v_qual JSONB;
        v_start TIMESTAMPTZ;
        v_end TIMESTAMPTZ;

        -- 사업관리 타이틀 풀
        v_biz_titles TEXT[] := ARRAY[
            '프로젝트 사업관리(PMO) 주니어 채용',
            '공공사업 PM 및 제안서 기획/운영 담당자',
            '글로벌 테크 비즈니스 전략 및 프로젝트 매니저',
            '정부지원 R&D 및 산학협력 사업관리자',
            'B2B 엔터프라이즈 솔루션 사업개발 매니저',
            '신규 IT 서비스 기획 및 일정/예산 관리자(PM)',
            '공공 SI 사업 제안 및 리스크 관리 전문가',
            '플랫폼 제휴 파트너십 및 프로젝트 운영 매니저'
            ];

        -- IT/SW 직무 타이틀 풀
        v_it_titles TEXT[] := ARRAY[
            'Java/Spring Boot 기반 대용량 트래픽 백엔드 엔지니어',
            '핀테크 코어 금융 결제 시스템 서버 개발자 (Kotlin/Spring)',
            '글로벌 이커머스 백엔드 API 플랫폼 개발자',
            'MSA 분산 아키텍처 기반 클라우드 백엔드 개발자',
            'React/TypeScript 기반 프론트엔드 엔지니어 (B2B SaaS)',
            '대규모 포털 서비스 프론트엔드 UI/UX 개발자 (Next.js)',
            '모바일 반응형 웹 프론트엔드 개발자 (Vue.js/React)',
            '웹 성능 최적화 및 공통 디자인 시스템 프론트엔드 엔지니어',
            '생성형 AI 서비스 및 RAG 파이프라인 엔지니어 (Python/LangChain)',
            '대용량 실시간 데이터 플랫폼 엔지니어 (Kafka/Spark)',
            'AI 추천 시스템 및 pgvector 코사인 유사도 검색 엔지니어',
            'NLP 자연어 처리 및 거대 언어 모델(LLM) 튜닝 연구원',
            'AWS/Kubernetes 기반 클라우드 DevOps 엔지니어',
            'SRE 인프라 가용성 및 분산 시스템 모니터링 엔지니어',
            '클라우드 인프라 보안 및 DevSecOps 운영 담당자',
            'Flutter/React Native 기반 크로스플랫폼 앱 개발자',
            'iOS/Android 네이티브 모바일 애플리케이션 개발자',
            'Node.js/TypeScript 풀스택 개발자 (스타트업 제품군)'
            ];

        -- JSONB 배열로 선언하여 차원 불일치 에러 원천 차단
        v_tech_stacks JSONB[] := ARRAY[
            '["Java", "Spring Boot", "JPA", "PostgreSQL", "Redis"]'::jsonb,
            '["React", "TypeScript", "Next.js", "TailwindCSS"]'::jsonb,
            '["Python", "FastAPI", "PyTorch", "Vector DB", "LangChain"]'::jsonb,
            '["AWS", "Kubernetes", "Docker", "Terraform", "CI/CD"]'::jsonb,
            '["Kotlin", "Kafka", "MySQL", "MSA", "Docker"]'::jsonb,
            '["Flutter", "Dart", "Firebase", "REST API"]'::jsonb,
            '["Node.js", "NestJS", "TypeScript", "GraphQL", "MongoDB"]'::jsonb
            ];

        v_benefits TEXT[] := ARRAY['SCHOLARSHIP', 'TUITION', 'HOUSING', 'INCENTIVE', 'EQUIPMENT'];
    BEGIN
        -- 1. 기업 계정 확인
        SELECT company_account_id INTO v_company_id FROM company_account LIMIT 1;
        IF v_company_id IS NULL THEN
            RAISE EXCEPTION 'company_account 테이블에 등록된 기업 계정이 없습니다.';
        END IF;

        -- 2. 등록된 지역 코드 배열 수집
        SELECT array_agg(code_id) INTO v_region_ids
        FROM common_code
        WHERE code_group = 'REGION_CODE' AND is_active = true;

        IF v_region_ids IS NULL OR array_length(v_region_ids, 1) = 0 THEN
            SELECT array_agg(code_id) INTO v_region_ids FROM common_code LIMIT 5;
        END IF;
        v_region_count := array_length(v_region_ids, 1);

        -- 3. NCS 코드 매핑
        SELECT code_id INTO v_ncs_biz_id FROM common_code
        WHERE code_group = 'NCS_CODE' AND (code_name LIKE '%사업%' OR code_name LIKE '%관리%') LIMIT 1;

        SELECT code_id INTO v_ncs_sw_id FROM common_code
        WHERE code_group = 'NCS_CODE' AND (code_name LIKE '%정보%' OR code_name LIKE '%소프트웨어%' OR code_name LIKE '%IT%' OR code_name LIKE '%개발%') LIMIT 1;

        SELECT code_id INTO v_ncs_default_id FROM common_code WHERE code_group = 'NCS_CODE' LIMIT 1;

        IF v_ncs_biz_id IS NULL THEN v_ncs_biz_id := v_ncs_default_id; END IF;
        IF v_ncs_sw_id IS NULL THEN v_ncs_sw_id := v_ncs_default_id; END IF;

        -- ==========================================
        -- [A] 사업관리 직무 30건
        -- ==========================================
        FOR i IN 1..30 LOOP
                v_title := v_biz_titles[((i - 1) % array_length(v_biz_titles, 1)) + 1] || ' (No.' || (1000 + i) || ')';
                v_reg_id := v_region_ids[((i - 1) % v_region_count) + 1];

                IF i % 5 = 0 THEN v_emp_type := '계약직';
                ELSIF i % 7 = 0 THEN v_emp_type := '인턴';
                ELSE v_emp_type := '정규직';
                END IF;

                IF i % 3 = 0 THEN
                    v_post_type := 'RECOMMENDED';
                    v_benefit := v_benefits[((i - 1) % array_length(v_benefits, 1)) + 1];
                ELSE
                    v_post_type := 'GENERAL';
                    v_benefit := NULL;
                END IF;

                IF i % 6 = 0 THEN
                    v_salary := NULL;
                ELSIF i % 4 = 0 THEN
                    v_salary := '회사 내규에 따름 (면접 후 결정)';
                ELSE
                    v_salary := (3200 + (i % 8) * 150) || '만원 ~ ' || (4000 + (i % 8) * 250) || '만원';
                END IF;

                v_start := NOW() - (INTERVAL '1 day' * (i % 10));
                v_end := NOW() + (INTERVAL '1 day' * (2 + (i * 3) % 45));

                v_desc := '【직무 개요】' || E'\n' ||
                          '- 사업 계획 수립, 공공/민간 프로젝트 제안서 기획 및 리스크 관리' || E'\n' ||
                          '- WBS 기반 마일스톤 관리 및 고객사 커뮤니케이션 조율' || E'\n\n' ||
                          '【주요 업무】' || E'\n' ||
                          '1. 프로젝트 사업비 집행 및 인력 투입 공수 산정' || E'\n' ||
                          '2. 제안서 작성, RFP 기술 분석 및 PT 기획' || E'\n' ||
                          '3. 사내 유관부서(개발/디자인/영업) 협업 관리' || E'\n\n' ||
                          '【우대 사항】' || E'\n' ||
                          '- 컴퓨터활용능력 1급 또는 정보처리기사 자격증 소지자' || E'\n' ||
                          '- 공공기관 과제 수행 및 정산 유경험자';

                IF i % 8 = 0 THEN
                    v_qual := NULL;
                ELSE
                    v_qual := jsonb_build_object(
                            'requiredEducation', CASE WHEN i % 2 = 0 THEN '학사 이상' ELSE '학력 무관' END,
                            'requiredExperience', CASE WHEN i % 3 = 0 THEN '신입' ELSE '경력 1~3년' END,
                            'certifications', jsonb_build_array('PMP', '컴퓨터활용능력', '정보처리기사')
                              );
                END IF;

                INSERT INTO job_posting (
                    created_at, updated_at, application_starts_at, application_ends_at,
                    posting_type, posting_status, review_status, published_at,
                    posting_title, job_description, company_account_id,
                    ncs_code_id, region_code_id, employment_type,
                    salary_text, benefit_type, qualification_data, recruitment_count
                ) VALUES (
                             NOW(), NOW(), v_start, v_end,
                             v_post_type, 'PUBLISHED', 'APPROVED', v_start,
                             v_title, v_desc, v_company_id,
                             v_ncs_biz_id, v_reg_id, v_emp_type,
                             v_salary, v_benefit, v_qual, 1 + (i % 3)
                         );
            END LOOP;

        -- ==========================================
        -- [B] 정보통신 / IT 직무 100건
        -- ==========================================
        FOR i IN 1..100 LOOP
                v_title := v_it_titles[((i - 1) % array_length(v_it_titles, 1)) + 1] || ' (채용 No.' || (2000 + i) || ')';
                v_reg_id := v_region_ids[((i - 1) % v_region_count) + 1];

                IF i % 6 = 0 THEN v_emp_type := '계약직';
                ELSIF i % 8 = 0 THEN v_emp_type := '인턴';
                ELSE v_emp_type := '정규직';
                END IF;

                IF i % 4 = 0 THEN
                    v_post_type := 'RECOMMENDED';
                    v_benefit := v_benefits[((i - 1) % array_length(v_benefits, 1)) + 1];
                ELSE
                    v_post_type := 'GENERAL';
                    v_benefit := NULL;
                END IF;

                IF i % 7 = 0 THEN
                    v_salary := NULL;
                ELSIF i % 5 = 0 THEN
                    v_salary := '면접 후 협의 (경력에 따라 차등 지급)';
                ELSE
                    v_salary := (3600 + (i % 15) * 200) || '만원 ~ ' || (4800 + (i % 15) * 300) || '만원';
                END IF;

                v_start := NOW() - (INTERVAL '1 day' * (i % 15));
                v_end := NOW() + (INTERVAL '1 day' * (1 + (i * 2) % 55));

                v_desc := '【기술 스택 및 담당 직무】' || E'\n' ||
                          '- 대규모 분산 환경 웹/모바일 서비스 구축 및 운영' || E'\n' ||
                          '- 주요 기술 스택 기반 MSA 아키텍처 구현' || E'\n\n' ||
                          '【주요 업무】' || E'\n' ||
                          '1. 마이크로서비스(MSA) RESTful API 및 사용자 인터페이스(UI) 설계 및 개발' || E'\n' ||
                          '2. 데이터베이스 쿼리 튜닝, 캐싱 전략(Redis) 적용 및 안정적 인프라 배포' || E'\n' ||
                          '3. 코드 리뷰 문화 및 단위 테스트(TDD) 기반 클린 코드 작성' || E'\n\n' ||
                          '【우대 사항】' || E'\n' ||
                          '- 컴퓨터공학/소프트웨어 관련 전공자' || E'\n' ||
                          '- 오픈소스 기여 경험 및 기술 블로그 운영자';

                IF i % 9 = 0 THEN
                    v_qual := NULL;
                ELSE
                    v_qual := jsonb_build_object(
                            'requiredEducation', CASE WHEN i % 4 = 0 THEN '학사 이상' ELSE '학력 무관' END,
                            'techStack', v_tech_stacks[((i - 1) % array_length(v_tech_stacks, 1)) + 1],
                            'requiredExperience', CASE WHEN i % 3 = 0 THEN '신입 가능' ELSE '경력 1년 이상' END
                              );
                END IF;

                INSERT INTO job_posting (
                    created_at, updated_at, application_starts_at, application_ends_at,
                    posting_type, posting_status, review_status, published_at,
                    posting_title, job_description, company_account_id,
                    ncs_code_id, region_code_id, employment_type,
                    salary_text, benefit_type, qualification_data, recruitment_count
                ) VALUES (
                             NOW(), NOW(), v_start, v_end,
                             v_post_type, 'PUBLISHED', 'APPROVED', v_start,
                             v_title, v_desc, v_company_id,
                             v_ncs_sw_id, v_reg_id, v_emp_type,
                             v_salary, v_benefit, v_qual, 1 + (i % 4)
                         );
            END LOOP;

        RAISE NOTICE '성공: 사업관리 30건, 정보통신 100건(총 130건) 다양한 조건의 공고가 삽입되었습니다.';
    END $$;